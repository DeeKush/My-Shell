import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    private static Path currentDirectory =
            Paths.get("").toAbsolutePath().normalize();

    private static final List<Job> jobs = new ArrayList<>();

    private static class Job {
        private final int jobNumber;
        private final Process process;
        private final String command;

        Job(int jobNumber, Process process, String command) {
            this.jobNumber = jobNumber;
            this.process = process;
            this.command = command;
        }
    }

    private static boolean isBuiltin(
            String command
    ) {
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type")
                || command.equals("pwd")
                || command.equals("cd")
                || command.equals("jobs");
    }

    private static String findExecutable(
            String command
    ) {
        String pathEnvironment =
                System.getenv("PATH");

        if (pathEnvironment == null
                || pathEnvironment.isEmpty()) {
            return null;
        }

        String[] directories =
                pathEnvironment.split(File.pathSeparator);

        for (String directory : directories) {
            File executable =
                    new File(directory, command);

            if (executable.isFile()
                    && executable.canExecute()) {
                return executable.getPath();
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

        /*
         * Find the last builtin in the pipeline.
         *
         * Builtins such as echo and type do not consume stdin.
         * Therefore, the last builtin determines the data sent
         * to any remaining external stages.
         */
        int lastBuiltinIndex = -1;

        for (int i = 0; i < stages.size(); i++) {
            List<String> stage = stages.get(i);

            if (!stage.isEmpty() && isBuiltin(stage.get(0))) {
                lastBuiltinIndex = i;
            }
        }

        /*
         * No builtins: run all external stages as one real
         * operating-system pipeline.
         */
        if (lastBuiltinIndex == -1) {
            runExternalPipeline(
                    stages,
                    stdoutFile,
                    appendStdout,
                    stderrFile,
                    appendStderr
            );

            return;
        }

        /*
         * Execute the last builtin. Earlier pipeline stages are
         * ignored because this builtin does not consume stdin.
         *
         * This makes:
         * ls | type exit
         *
         * print only:
         * exit is a shell builtin
         */
        String builtinOutput =
                executeBuiltinForPipeline(
                        stages.get(lastBuiltinIndex),
                        stderrFile,
                        appendStderr
                );

        /*
         * If the builtin is the final stage, print its output.
         */
        if (lastBuiltinIndex == stages.size() - 1) {
            writeExactOutput(
                    builtinOutput,
                    stdoutFile,
                    appendStdout
            );

            return;
        }

        /*
         * All stages after the last builtin must be external.
         * Send the builtin output into the first remaining
         * process, then connect all remaining stages together.
         */
        List<List<String>> remainingStages =
                new ArrayList<>();

        for (int i = lastBuiltinIndex + 1;
             i < stages.size();
             i++) {

            remainingStages.add(stages.get(i));
        }

        runExternalPipelineWithInput(
                remainingStages,
                builtinOutput.getBytes(StandardCharsets.UTF_8),
                stdoutFile,
                appendStdout,
                stderrFile,
                appendStderr
        );
    }

    private static void runExternalPipeline(
            List<List<String>> stages,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr
    ) throws Exception {

        List<ProcessBuilder> builders = new ArrayList<>();

        for (List<String> stage : stages) {
            if (stage.isEmpty()) {
                return;
            }

            String programName = stage.get(0);

            if (findExecutable(programName) == null) {
                writeError(
                        programName + ": command not found",
                        stderrFile,
                        appendStderr
                );

                return;
            }

            ProcessBuilder builder =
                    new ProcessBuilder(stage);

            builder.directory(currentDirectory.toFile());

            /*
             * Each pipeline process keeps its errors connected
             * to the terminal.
             */
            builder.redirectError(
                    ProcessBuilder.Redirect.INHERIT
            );

            builders.add(builder);
        }

        /*
         * The first process reads from the shell's stdin.
         */
        builders.get(0).redirectInput(
                ProcessBuilder.Redirect.INHERIT
        );

        /*
         * The final process writes to the terminal or a
         * redirected file.
         */
        configureFinalPipelineOutput(
                builders.get(builders.size() - 1),
                stdoutFile,
                appendStdout,
                stderrFile,
                appendStderr
        );

        List<Process> processes =
                ProcessBuilder.startPipeline(builders);

        Process lastProcess =
                processes.get(processes.size() - 1);

        /*
         * Wait until the final pipeline process exits.
         */
        lastProcess.waitFor();

        /*
         * Stop any upstream process that is still alive.
         *
         * This is needed for:
         * tail -f file | head -n 5
         *
         * head exits after five lines, but tail -f otherwise
         * keeps running forever.
         */
        for (int i = 0; i < processes.size() - 1; i++) {
            stopProcess(processes.get(i));
        }
    }

    private static void runExternalPipelineWithInput(
            List<List<String>> stages,
            byte[] input,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr
    ) throws Exception {

        if (stages.isEmpty()) {
            writeExactOutput(
                    new String(input, StandardCharsets.UTF_8),
                    stdoutFile,
                    appendStdout
            );

            return;
        }

        List<ProcessBuilder> builders = new ArrayList<>();

        for (List<String> stage : stages) {
            if (stage.isEmpty()) {
                return;
            }

            String programName = stage.get(0);

            if (findExecutable(programName) == null) {
                writeError(
                        programName + ": command not found",
                        stderrFile,
                        appendStderr
                );

                return;
            }

            ProcessBuilder builder =
                    new ProcessBuilder(stage);

            builder.directory(currentDirectory.toFile());

            builder.redirectError(
                    ProcessBuilder.Redirect.INHERIT
            );

            builders.add(builder);
        }

        configureFinalPipelineOutput(
                builders.get(builders.size() - 1),
                stdoutFile,
                appendStdout,
                stderrFile,
                appendStderr
        );

        /*
         * A single external process does not require
         * startPipeline().
         */
        if (builders.size() == 1) {
            Process process = builders.get(0).start();

            writeToProcessInput(process, input);

            process.waitFor();

            return;
        }

        List<Process> processes =
                ProcessBuilder.startPipeline(builders);

        /*
         * Write the builtin output to the stdin of the first
         * external process.
         */
        writeToProcessInput(
                processes.get(0),
                input
        );

        Process lastProcess =
                processes.get(processes.size() - 1);

        lastProcess.waitFor();

        for (int i = 0; i < processes.size() - 1; i++) {
            stopProcess(processes.get(i));
        }
    }

    private static void configureFinalPipelineOutput(
            ProcessBuilder builder,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr
    ) {
        if (stdoutFile == null) {
            builder.redirectOutput(
                    ProcessBuilder.Redirect.INHERIT
            );
        } else {
            File file = resolvePath(stdoutFile).toFile();

            if (appendStdout) {
                builder.redirectOutput(
                        ProcessBuilder.Redirect.appendTo(file)
                );
            } else {
                builder.redirectOutput(
                        ProcessBuilder.Redirect.to(file)
                );
            }
        }

        if (stderrFile == null) {
            builder.redirectError(
                    ProcessBuilder.Redirect.INHERIT
            );
        } else {
            File file = resolvePath(stderrFile).toFile();

            if (appendStderr) {
                builder.redirectError(
                        ProcessBuilder.Redirect.appendTo(file)
                );
            } else {
                builder.redirectError(
                        ProcessBuilder.Redirect.to(file)
                );
            }
        }
    }

    private static void writeToProcessInput(
            Process process,
            byte[] input
    ) {
        try (OutputStream outputStream =
                     process.getOutputStream()) {

            outputStream.write(input);
            outputStream.flush();

        } catch (IOException ignored) {
            /*
             * Some commands close stdin before all bytes are
             * written. That is safe for this shell.
             */
        }
    }

    private static String executeBuiltinForPipeline(
            List<String> commandParts,
            String stderrFile,
            boolean appendStderr
    ) throws IOException {

        if (commandParts.isEmpty()) {
            return "";
        }

        String command = commandParts.get(0);
        String newline = System.lineSeparator();

        if (command.equals("echo")) {
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

            return output + newline;
        }

        if (command.equals("pwd")) {
            return currentDirectory + newline;
        }

        if (command.equals("type")) {
            if (commandParts.size() < 2) {
                return "";
            }

            return getTypeResult(commandParts.get(1))
                    + newline;
        }

        if (command.equals("jobs")) {
            String output = buildJobsOutput();

            if (output.isEmpty()) {
                return "";
            }

            return output + newline;
        }

        if (command.equals("cd")) {
            if (commandParts.size() >= 2) {
                changeDirectory(
                        commandParts.get(1),
                        stderrFile,
                        appendStderr
                );
            }

            return "";
        }

        return "";
    }

    private static void reapProcess(Process process) {
        try {
            process.waitFor();

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void stopProcess(Process process) {
        if (!process.isAlive()) {
            reapProcess(process);
            return;
        }

        process.destroy();

        try {
            if (!process.waitFor(
                    100,
                    TimeUnit.MILLISECONDS
            )) {
                process.destroyForcibly();
                process.waitFor();
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasProcessFinished(
            Process process
    ) {
        if (!process.isAlive()) {
            return true;
        }

        try {
            /*
             * Avoid a timing race when a FIFO closes and the
             * process exits a few milliseconds later.
             */
            return process.waitFor(
                    100,
                    TimeUnit.MILLISECONDS
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String getJobMarker(
            Job job,
            Job currentJob,
            Job currentPreviousJob
    ) {
        if (job == currentJob) {
            return "+";
        }

        if (job == currentPreviousJob) {
            return "-";
        }

        return " ";
    }

    private static int getSmallestAvailableJobNumber() {
        int candidate = 1;

        while (true) {
            boolean used = false;

            for (Job job : jobs) {
                if (job.jobNumber == candidate) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                return candidate;
            }

            candidate++;
        }
    }

    private static String removeTrailingAmpersand(
            String command
    ) {
        return command.replaceFirst(
                "\\s*&\\s*$",
                ""
        );
    }

    private static void handleJobs(
            String stdoutFile,
            boolean appendStdout
    ) throws IOException {

        String output = buildJobsOutput();

        if (output.isEmpty()) {
            if (stdoutFile != null) {
                if (appendStdout) {
                    ensureFileExists(stdoutFile);
                } else {
                    truncateFile(stdoutFile);
                }
            }

            return;
        }

        writeOutput(
                output,
                stdoutFile,
                appendStdout
        );
    }

    private static String buildJobsOutput() {
        if (jobs.isEmpty()) {
            return "";
        }

        Job currentJob =
                jobs.get(jobs.size() - 1);

        Job previousJob = jobs.size() >= 2
                ? jobs.get(jobs.size() - 2)
                : null;

        List<Job> jobsToDisplay =
                new ArrayList<>(jobs);

        jobsToDisplay.sort(
                (first, second) ->
                        Integer.compare(
                                first.jobNumber,
                                second.jobNumber
                        )
        );

        StringBuilder output = new StringBuilder();
        List<Job> completedJobs = new ArrayList<>();

        for (int i = 0; i < jobsToDisplay.size(); i++) {
            Job job = jobsToDisplay.get(i);

            String marker =
                    getJobMarker(
                            job,
                            currentJob,
                            previousJob
                    );

            boolean finished =
                    hasProcessFinished(job.process);

            String status;
            String displayedCommand;

            if (finished) {
                status = "Done";

                displayedCommand =
                        removeTrailingAmpersand(job.command);

                reapProcess(job.process);
                completedJobs.add(job);

            } else {
                status = "Running";
                displayedCommand = job.command;
            }

            output.append(
                    String.format(
                            "[%d]%s  %-24s%s",
                            job.jobNumber,
                            marker,
                            status,
                            displayedCommand
                    )
            );

            if (i < jobsToDisplay.size() - 1) {
                output.append(System.lineSeparator());
            }
        }

        jobs.removeAll(completedJobs);

        return output.toString();
    }

    private static void runExternalCommand(
            List<String> commandParts,
            String stdoutFile,
            boolean appendStdout,
            String stderrFile,
            boolean appendStderr,
            boolean runInBackground,
            String originalCommand
    ) throws Exception {

        String programName = commandParts.get(0);

        if (findExecutable(programName) == null) {
            writeError(
                    programName + ": command not found",
                    stderrFile,
                    appendStderr
            );

            return;
        }

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

        if (runInBackground) {
            int jobNumber =
                    getSmallestAvailableJobNumber();

            jobs.add(
                    new Job(
                            jobNumber,
                            process,
                            originalCommand
                    )
            );

            System.out.println(
                    "[" + jobNumber + "] "
                            + process.pid()
            );

        } else {
            process.waitFor();
        }
    }

    private static void reapCompletedJobsBeforePrompt() {
        if (jobs.isEmpty()) {
            return;
        }

        Job currentJob =
                jobs.get(jobs.size() - 1);

        Job previousJob = jobs.size() >= 2
                ? jobs.get(jobs.size() - 2)
                : null;

        List<Job> jobsToCheck =
                new ArrayList<>(jobs);

        jobsToCheck.sort(
                (first, second) ->
                        Integer.compare(
                                first.jobNumber,
                                second.jobNumber
                        )
        );

        List<Job> completedJobs = new ArrayList<>();

        for (Job job : jobsToCheck) {
            if (hasProcessFinished(job.process)) {
                String marker =
                        getJobMarker(
                                job,
                                currentJob,
                                previousJob
                        );

                reapProcess(job.process);

                System.out.printf(
                        "[%d]%s  %-24s%s%n",
                        job.jobNumber,
                        marker,
                        "Done",
                        removeTrailingAmpersand(job.command)
                );

                completedJobs.add(job);
            }
        }

        jobs.removeAll(completedJobs);
    }

    public static void main(String[] args) throws Exception {

        BufferedReader reader =
                new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            reapCompletedJobsBeforePrompt();

            System.out.print("$ ");
            System.out.flush();

            String input = reader.readLine();

            if (input == null) {
                break;
            }

            String originalCommand = input.trim();

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

            /*
             * A final & means background execution.
             */
            boolean runInBackground = false;

            if (!commandParts.isEmpty()
                    && commandParts
                    .get(commandParts.size() - 1)
                    .equals("&")) {

                runInBackground = true;
                commandParts.remove(commandParts.size() - 1);
            }

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

            } else if (command.equals("jobs")) {
                handleJobs(
                        stdoutFile,
                        appendStdout
                );

            } else {
                runExternalCommand(
                        commandParts,
                        stdoutFile,
                        appendStdout,
                        stderrFile,
                        appendStderr,
                        runInBackground,
                        originalCommand
                );
            }
        }
    }
}
