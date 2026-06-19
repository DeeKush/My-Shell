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

    private static void handleEcho(
            List<String> commandParts,
            String stdoutFile,
            boolean appendStdout
    ) throws IOException {

        String output = "";

        if (commandParts.size() > 1) {
            output = String.join(
                    " ",
                    commandParts.subList(
                            1,
                            commandParts.size()
                    )
            );
        }

        writeOutput(
                output,
                stdoutFile,
                appendStdout
        );
    }

    private static void changeDirectory(
            String directory,
            String stderrFile,
            boolean appendStderr
    ) throws IOException {

        Path newDirectory;

        if (directory.equals("~")) {
            String home = System.getenv("HOME");

            if (home == null || home.isEmpty()) {
                writeError(
                        "cd: ~: No such file or directory",
                        stderrFile,
                        appendStderr
                );

                return;
            }

            newDirectory = Paths.get(home);

        } else {
            Path requestedPath =
                    Paths.get(directory);

            if (requestedPath.isAbsolute()) {
                newDirectory = requestedPath;
            } else {
                newDirectory =
                        currentDirectory.resolve(requestedPath);
            }
        }

        newDirectory =
                newDirectory.toAbsolutePath().normalize();

        if (Files.isDirectory(newDirectory)) {
            currentDirectory = newDirectory;

        } else {
            writeError(
                    "cd: " + directory
                            + ": No such file or directory",
                    stderrFile,
                    appendStderr
            );
        }
    }

    private static String getTypeResult(
            String command
    ) {
        if (isBuiltin(command)) {
            return command + " is a shell builtin";
        }

        String executablePath =
                findExecutable(command);

        if (executablePath != null) {
            return command + " is " + executablePath;
        }

        return command + ": not found";
    }

    private static List<List<String>> splitPipeline(
            List<String> tokens
    ) {
        List<List<String>> stages = new ArrayList<>();
        List<String> currentStage = new ArrayList<>();

        for (String token : tokens) {
            if (token.equals("|")) {
                if (currentStage.isEmpty()) {
                    return null;
                }

                stages.add(currentStage);
                currentStage = new ArrayList<>();
            } else {
                currentStage.add(token);
            }
        }

        if (currentStage.isEmpty()) {
            return null;
        }

        stages.add(currentStage);

        return stages;
    }

    private static void runPipeline(
            List<String> tokens,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr
    ) throws Exception {

        List<List<String>> stages = splitPipeline(tokens);

        if (stages == null || stages.size() < 2) {
            return;
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String input = scanner.nextLine();

            List<String> commandParts = parseCommandLine(input);

            if (commandParts.isEmpty()) {
                continue;
            }

            String stdoutFile = null;
            String stderrFile = null;

            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> actualCommandParts = new ArrayList<>();

            for (int i = 0; i < commandParts.size(); i++) {
                String token = commandParts.get(i);

                if (token.equals(">") || token.equals("1>")) {
                    if (i + 1 < commandParts.size()) {
                        stdoutFile = commandParts.get(++i);
                        appendStdout = false;
                    }

                } else if (token.equals(">>")
                        || token.equals("1>>")) {

                    if (i + 1 < commandParts.size()) {
                        stdoutFile = commandParts.get(++i);
                        appendStdout = true;
                    }

                } else if (token.equals("2>")) {
                    if (i + 1 < commandParts.size()) {
                        stderrFile = commandParts.get(++i);
                        appendStderr = false;
                    }

                } else if (token.equals("2>>")) {
                    if (i + 1 < commandParts.size()) {
                        stderrFile = commandParts.get(++i);
                        appendStderr = true;
                    }

                } else {
                    actualCommandParts.add(token);
                }
            }

            commandParts = actualCommandParts;

            if (commandParts.isEmpty()) {
                continue;
            }

            if (stdoutFile != null && !appendStdout) {
                truncateFile(stdoutFile);
            }
            if (stderrFile != null && !appendStderr) {
                truncateFile(stderrFile);
            }

            if (commandParts.contains("|")) {
                runPipeline(
                        commandParts,
                        stdoutFile,
                        appendStdout,
                        stderrFile,
                        appendStderr
                );

                continue;
            }

            String command = commandParts.get(0);

            if (command.equals("exit")) {
                break;

            } else if (command.equals("echo")) {
                handleEcho(
                        commandParts,
                        stdoutFile,
                        appendStdout
                );

            } else if (command.equals("pwd")) {
                writeOutput(
                        currentDirectory.toString(),
                        stdoutFile,
                        appendStdout
                );

            } else if (command.equals("cd")) {
                if (commandParts.size() >= 2) {
                    changeDirectory(
                            commandParts.get(1),
                            stderrFile,
                            appendStderr
                    );
                }

            } else if (command.equals("type")) {
                if (commandParts.size() >= 2) {
                    writeOutput(
                            getTypeResult(commandParts.get(1)),
                            stdoutFile,
                            appendStdout
                    );
                }

            } else {
                String programName = commandParts.get(0);

                if (findExecutable(programName) == null) {
                    writeError(
                            programName + ": command not found",
                            stderrFile,
                            appendStderr
                    );

                    continue;
                }

                try {
                    ProcessBuilder processBuilder =
                            new ProcessBuilder(commandParts);

                    processBuilder.directory(
                            currentDirectory.toFile()
                    );

                    processBuilder.redirectInput(
                            ProcessBuilder.Redirect.INHERIT
                    );

                    if (stdoutFile == null) {
                        processBuilder.redirectOutput(
                                ProcessBuilder.Redirect.INHERIT
                        );

                    } else {
                        File file = resolvePath(stdoutFile).toFile();

                        if (appendStdout) {
                            processBuilder.redirectOutput(
                                    ProcessBuilder.Redirect.appendTo(file)
                            );
                        } else {
                            processBuilder.redirectOutput(
                                    ProcessBuilder.Redirect.to(file)
                            );
                        }
                    }

                    if (stderrFile == null) {
                        processBuilder.redirectError(
                                ProcessBuilder.Redirect.INHERIT
                        );

                    } else {
                        File file = resolvePath(stderrFile).toFile();

                        if (appendStderr) {
                            processBuilder.redirectError(
                                    ProcessBuilder.Redirect.appendTo(file)
                            );
                        } else {
                            processBuilder.redirectError(
                                    ProcessBuilder.Redirect.to(file)
                            );
                        }
                    }

                    Process process = processBuilder.start();
                    process.waitFor();

                } catch (IOException e) {
                    writeError(
                            programName + ": command not found",
                            stderrFile,
                            appendStderr
                    );
                }
            }
        }
    }
}
