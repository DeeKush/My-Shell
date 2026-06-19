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

    private static String[] parseCommand(String input) {

        List<String> args = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {

            char ch = input.charAt(i);

            // Backslash outside quotes
            if (!inSingleQuote
                    && !inDoubleQuote
                    && ch == '\\') {

                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }

                continue;
            }

            // Backslash inside double quotes
            if (inDoubleQuote && ch == '\\') {

                if (i + 1 < input.length()) {

                    char next = input.charAt(i + 1);

                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }

                    current.append('\\');
                    current.append(next);
                    i++;
                    continue;
                }
            }

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (Character.isWhitespace(ch)
                    && !inSingleQuote
                    && !inDoubleQuote) {

                if (current.length() > 0) {

                    args.add(current.toString());
                    current.setLength(0);
                }
            }

            else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString());
        }

        return args.toArray(new String[0]);
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
                    parseCommand(input);

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
