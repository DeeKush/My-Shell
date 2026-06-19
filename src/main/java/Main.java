import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Main {

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

    private static void createParentDirs(String filePath) {
        if (filePath != null) {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        }
    }

    private static void touchFile(String filePath) throws IOException {
        if (filePath != null) {
            createParentDirs(filePath);
            File file = new File(filePath);
            try (FileWriter fw = new FileWriter(file, false)) {
                // Truncate/create file
            }
        }
    }

    private static void writeOutput(
            String text,
            String redirectFile,
            boolean append) throws IOException {

        if (redirectFile != null) {
            createParentDirs(redirectFile);
            try (PrintWriter writer = new PrintWriter(
                    new FileWriter(
                            redirectFile,
                            append))) {

                writer.println(text);
            }
        } else {
            System.out.println(text);
        }
    }

    private static void writeError(
            String text,
            String redirectErrFile,
            boolean append) throws IOException {

        if (redirectErrFile != null) {
            createParentDirs(redirectErrFile);
            try (PrintWriter writer = new PrintWriter(
                    new FileWriter(
                            redirectErrFile,
                            append))) {

                writer.println(text);
            }
        } else {
            System.out.println(text);
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        File currentDirectory =
                new File(System.getProperty("user.dir"));

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
                touchFile(redirectFile);
            }
            if (redirectErrFile != null && !appendErr) {
                touchFile(redirectErrFile);
            }

            if (commandName.equals("exit")) {
                break;
            }

            else if (commandName.equals("pwd")) {

                writeOutput(
                        currentDirectory.getAbsolutePath(),
                        redirectFile,
                        appendOut
                );
            }

            else if (commandName.equals("cd")) {

                if (commandParts.length < 2) {
                    continue;
                }

                String path = commandParts[1];

                File targetDirectory;

                if (path.equals("~")) {

                    targetDirectory =
                            new File(
                                    System.getenv("HOME")
                            );

                } else if (new File(path).isAbsolute()) {

                    targetDirectory =
                            new File(path);

                } else {

                    targetDirectory =
                            new File(
                                    currentDirectory,
                                    path
                            );
                }

                targetDirectory =
                        targetDirectory.getCanonicalFile();

                if (targetDirectory.exists()
                        && targetDirectory.isDirectory()) {

                    currentDirectory =
                            targetDirectory;

                } else {

                    writeError(
                            "cd: "
                                    + path
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
                                currentDirectory
                        );

                        if (redirectFile != null) {
                            createParentDirs(redirectFile);
                            if (appendOut) {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.appendTo(new File(redirectFile))
                                );
                            } else {
                                pb.redirectOutput(
                                        ProcessBuilder.Redirect.to(new File(redirectFile))
                                );
                            }
                        } else {
                            pb.redirectOutput(
                                    ProcessBuilder.Redirect.INHERIT
                            );
                        }

                        if (redirectErrFile != null) {
                            createParentDirs(redirectErrFile);
                            if (appendErr) {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.appendTo(new File(redirectErrFile))
                                );
                            } else {
                                pb.redirectError(
                                        ProcessBuilder.Redirect.to(new File(redirectErrFile))
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
