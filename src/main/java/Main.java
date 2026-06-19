import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {

    private static Path currentDirectory =
            Paths.get("").toAbsolutePath().normalize();

    private static boolean isBuiltin(String cmd) {
        return cmd.equals("exit")
                || cmd.equals("echo")
                || cmd.equals("type")
                || cmd.equals("pwd")
                || cmd.equals("cd");
    }

    private static String findExecutable(String command) {

        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        String[] directories = path.split(Pattern.quote(File.pathSeparator));

        for (String dir : directories) {

            File file = new File(dir, command);

            if (file.exists()
                    && file.isFile()
                    && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private static List<String> parseCommandLine(String input) {
        List<String> arguments = new ArrayList<>();
        StringBuilder currentArgument = new StringBuilder();

        boolean insideSingleQuotes = false;
        boolean insideDoubleQuotes = false;
        boolean argumentStarted = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            /*
             * Everything inside single quotes is literal.
             */
            if (insideSingleQuotes) {
                if (ch == '\'') {
                    insideSingleQuotes = false;
                } else {
                    currentArgument.append(ch);
                }

                argumentStarted = true;
                continue;
            }

            /*
             * Inside double quotes, backslash escapes only
             * double quote and backslash for these stages.
             */
            if (insideDoubleQuotes) {
                if (ch == '"') {
                    insideDoubleQuotes = false;
                    argumentStarted = true;

                } else if (ch == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);

                        if (next == '"' || next == '\\') {
                            currentArgument.append(next);
                            i++;
                        } else {
                            currentArgument.append('\\');
                        }
                    } else {
                        currentArgument.append('\\');
                    }

                    argumentStarted = true;

                } else {
                    currentArgument.append(ch);
                    argumentStarted = true;
                }

                continue;
            }

            /*
             * Outside quotes.
             */
            if (ch == '\'') {
                insideSingleQuotes = true;
                argumentStarted = true;

            } else if (ch == '"') {
                insideDoubleQuotes = true;
                argumentStarted = true;

            } else if (ch == '\\') {
                if (i + 1 < input.length()) {
                    currentArgument.append(input.charAt(i + 1));
                    i++;
                } else {
                    currentArgument.append('\\');
                }

                argumentStarted = true;

            } else if (Character.isWhitespace(ch)) {
                if (argumentStarted) {
                    arguments.add(currentArgument.toString());
                    currentArgument.setLength(0);
                    argumentStarted = false;
                }

            } else {
                currentArgument.append(ch);
                argumentStarted = true;
            }
        }

        if (argumentStarted) {
            arguments.add(currentArgument.toString());
        }

        return arguments;
    }

    private static Path resolvePath(
            String fileName
    ) {
        Path path = Paths.get(fileName);

        if (path.isAbsolute()) {
            return path.normalize();
        }

        return currentDirectory
                .resolve(path)
                .normalize();
    }

    private static void truncateFile(
            String fileName
    ) throws IOException {

        Files.writeString(
                resolvePath(fileName),
                "",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private static void ensureFileExists(
            String fileName
    ) throws IOException {

        Path filePath = resolvePath(fileName);

        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
    }

    private static void writeOutput(
            String text,
            String stdoutFile,
            boolean appendStdout
    ) throws IOException {

        writeExactOutput(
                text + System.lineSeparator(),
                stdoutFile,
                appendStdout
        );
    }

    private static void writeExactOutput(
            String text,
            String stdoutFile,
            boolean appendStdout
    ) throws IOException {

        if (stdoutFile == null) {
            System.out.print(text);
            System.out.flush();
            return;
        }

        Path filePath = resolvePath(stdoutFile);

        if (appendStdout) {
            Files.writeString(
                    filePath,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            );

        } else {
            Files.writeString(
                    filePath,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        }
    }

    private static void writeError(
            String text,
            String stderrFile,
            boolean appendStderr
    ) throws IOException {

        if (stderrFile == null) {
            System.err.println(text);
            return;
        }

        Path filePath = resolvePath(stderrFile);
        String output =
                text + System.lineSeparator();

        if (appendStderr) {
            Files.writeString(
                    filePath,
                    output,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
            );

        } else {
            Files.writeString(
                    filePath,
                    output,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String input = scanner.nextLine();

            String[] commandParts =
                    parseCommandLine(input).toArray(new String[0]);

            String redirectFile = null;
            String redirectErrFile = null;
            boolean appendOut = false;
            boolean appendErr = false;

            List<String> cleanedArgs =
                    new ArrayList<>();

            for (int i = 0; i < commandParts.length; i++) {

                if (commandParts[i].equals(">") ||
                        commandParts[i].equals("1>")) {

                    if (i + 1 < commandParts.length) {
                        redirectFile =
                                commandParts[i + 1];
                        appendOut = false;
                    }

                    i++; // skip filename

                } else if (commandParts[i].equals(">>") ||
                        commandParts[i].equals("1>>")) {

                    if (i + 1 < commandParts.length) {
                        redirectFile =
                                commandParts[i + 1];
                        appendOut = true;
                    }

                    i++; // skip filename

                } else if (commandParts[i].equals("2>")) {

                    if (i + 1 < commandParts.length) {
                        redirectErrFile =
                                commandParts[i + 1];
                        appendErr = false;
                    }

                    i++; // skip filename

                } else if (commandParts[i].equals("2>>")) {

                    if (i + 1 < commandParts.length) {
                        redirectErrFile =
                                commandParts[i + 1];
                        appendErr = true;
                    }

                    i++; // skip filename

                } else {

                    cleanedArgs.add(
                            commandParts[i]
                    );
                }
            }

            commandParts =
                    cleanedArgs.toArray(
                            new String[0]
                    );

            if (commandParts.length == 0) {
                continue;
            }

            String commandName =
                    commandParts[0];

            // Touch files immediately to create/truncate them (only if in overwrite mode)
            if (redirectFile != null && !appendOut) {
                truncateFile(redirectFile);
            }
            if (redirectErrFile != null && !appendErr) {
                truncateFile(redirectErrFile);
            }

            if (commandName.equals("exit")) {
                break;
            }

            else if (commandName.equals("pwd")) {

                writeOutput(
                        currentDirectory.toString(),
                        redirectFile,
                        appendOut
                );
            }

            else if (commandName.equals("cd")) {

                if (commandParts.length < 2) {
                    continue;
                }

                String path = commandParts[1];

                Path targetDirectory;

                if (path.equals("~")) {
                    String home = System.getenv("HOME");

                    if (home == null || home.isEmpty()) {
                        writeError(
                                "cd: ~: No such file or directory",
                                redirectErrFile,
                                appendErr
                        );
                        continue;
                    }

                    targetDirectory = Paths.get(home);

                } else {
                    Path requestedPath = Paths.get(path);

                    if (requestedPath.isAbsolute()) {
                        targetDirectory = requestedPath;
                    } else {
                        targetDirectory =
                                currentDirectory.resolve(requestedPath);
                    }
                }

                targetDirectory =
                        targetDirectory.toAbsolutePath().normalize();

                if (Files.isDirectory(targetDirectory)) {
                    currentDirectory = targetDirectory;

                } else {
                    writeError(
                            "cd: " + path
                                    + ": No such file or directory",
                            redirectErrFile,
                            appendErr
                    );
                }
            }

            else if (commandName.equals("echo")) {

                StringBuilder output =
                        new StringBuilder();

                for (int i = 1;
                     i < commandParts.length;
                     i++) {

                    if (i > 1) {
                        output.append(" ");
                    }

                    output.append(
                            commandParts[i]
                    );
                }

                writeOutput(
                        output.toString(),
                        redirectFile,
                        appendOut
                );
            }

            else if (commandName.equals("type")) {

                if (commandParts.length < 2) {
                    continue;
                }

                String command =
                        commandParts[1];

                String output;

                if (isBuiltin(command)) {

                    output =
                            command
                                    + " is a shell builtin";

                } else {

                    String executable =
                            findExecutable(command);

                    if (executable != null) {

                        output =
                                command
                                        + " is "
                                        + executable;

                    } else {

                        output =
                                command
                                        + ": not found";
                    }
                }

                writeOutput(
                        output,
                        redirectFile,
                        appendOut
                );
            }

            else {

                String executable =
                        findExecutable(commandName);

                if (executable != null) {

                    try {

                        ProcessBuilder pb =
                                new ProcessBuilder(
                                        commandParts
                                );

                        pb.directory(
                                currentDirectory.toFile()
                        );

                        if (redirectFile != null) {
                            if (appendOut) {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.appendTo(resolvePath(redirectFile).toFile())
                                );
                            } else {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.to(resolvePath(redirectFile).toFile())
                                );
                            }
                        } else {
                            pb.redirectOutput(
                                    ProcessBuilder.Redirect.INHERIT
                            );
                        }

                        if (redirectErrFile != null) {
                            if (appendErr) {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.appendTo(resolvePath(redirectErrFile).toFile())
                                );
                            } else {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.to(resolvePath(redirectErrFile).toFile())
                                );
                            }
                        } else {
                            pb.redirectError(
                                    ProcessBuilder.Redirect.INHERIT
                            );
                        }

                        pb.redirectInput(
                                ProcessBuilder.Redirect.INHERIT
                        );

                        Process process =
                                pb.start();

                        process.waitFor();

                    } catch (IOException e) {

                        writeError(
                                commandName
                                        + ": command not found",
                                redirectErrFile,
                                appendErr
                        );
                    }

                } else {

                    writeError(
                            commandName
                                    + ": command not found",
                            redirectErrFile,
                            appendErr
                    );
                }
            }
        }
    }
}
