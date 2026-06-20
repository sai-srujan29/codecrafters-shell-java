import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static String cwd = System.getProperty("user.dir");
    private static int jobCounter = 1;

    static class Job {
        int id;
        Process process;
        String command;
        String status;

        Job(int id, Process process, String command, String status) {
            this.id = id;
            this.process = process;
            this.command = command;
            this.status = status;
        }
    }

    private static List<Job> jobsList = new ArrayList<>();

    private static void reapJobs(java.io.PrintWriter outWriter, boolean printRunning) {
        int numJobs = jobsList.size();
        List<Job> toRemove = new ArrayList<>();
        for (int i = 0; i < numJobs; i++) {
            Job job = jobsList.get(i);
            char marker = ' ';
            if (i == numJobs - 1) {
                marker = '+';
            } else if (i == numJobs - 2) {
                marker = '-';
            }

            if (!job.process.isAlive()) {
                String cmdDisplay = job.command.trim();
                if (cmdDisplay.endsWith("&")) {
                    cmdDisplay = cmdDisplay.substring(0, cmdDisplay.length() - 1).trim();
                }
                String statusFormatted = String.format("%-24s", "Done");
                String line = "[" + job.id + "]" + marker + "  " + statusFormatted + cmdDisplay;
                if (outWriter != null) {
                    outWriter.println(line);
                } else {
                    System.out.println(line);
                }
                toRemove.add(job);
            } else if (printRunning) {
                String statusFormatted = String.format("%-24s", job.status);
                String line = "[" + job.id + "]" + marker + "  " + statusFormatted + job.command;
                if (outWriter != null) {
                    outWriter.println(line);
                } else {
                    System.out.println(line);
                }
            }
        }
        jobsList.removeAll(toRemove);
    }

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inArg = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    currentArg.append(c);
                }
                inArg = true;
            } else if (inDoubleQuote) {
                if (c == '\"') {
                    inDoubleQuote = false;
                } else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\"' || next == '\\' || next == '$' || next == '`') {
                            currentArg.append(next);
                            i++;
                        } else {
                            currentArg.append(c);
                        }
                    } else {
                        currentArg.append(c);
                    }
                } else {
                    currentArg.append(c);
                }
                inArg = true;
            } else {
                if (c == '\'') {
                    inSingleQuote = true;
                    inArg = true;
                } else if (c == '\"') {
                    inDoubleQuote = true;
                    inArg = true;
                } else if (c == '\\') {
                    if (i + 1 < input.length()) {
                        currentArg.append(input.charAt(++i));
                        inArg = true;
                    }
                } else if (Character.isWhitespace(c)) {
                    if (inArg) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0);
                        inArg = false;
                    }
                } else {
                    currentArg.append(c);
                    inArg = true;
                }
            }
        }

        if (inArg) {
            args.add(currentArg.toString());
        }

        return args;
    }

    private static File getExecutable(String command) {
        File commandFile = new File(command);
        if (commandFile.isAbsolute() || command.contains("/") || command.contains(File.separator)) {
            if (commandFile.exists() && commandFile.canExecute()) {
                return commandFile;
            }
        }

        String path = System.getenv("PATH");

        if (path == null) {
            return null;
        }

        String[] pathDirs = path.split(File.pathSeparator);

        for (String dir : pathDirs) {
            File file = new File(dir, command);

            if (file.exists() && file.canExecute()) {
                return file;
            }
        }

        return null;
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            reapJobs(null, false);
            System.out.print("$ ");

            String input = scanner.nextLine();
            List<String> cmdArgs = parseArguments(input);

            if (cmdArgs.isEmpty()) {
                continue;
            }

            boolean isBackground = false;
            if (cmdArgs.get(cmdArgs.size() - 1).equals("&")) {
                isBackground = true;
                cmdArgs.remove(cmdArgs.size() - 1);
            }

            if (cmdArgs.isEmpty()) {
                continue;
            }

            String command = cmdArgs.get(0);

            String outputFile = null;
            boolean appendOutput = false;
            String errorFile = null;
            boolean appendError = false;
            for (int i = 0; i < cmdArgs.size(); i++) {
                String arg = cmdArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < cmdArgs.size()) {
                        outputFile = cmdArgs.get(i + 1);
                        appendOutput = false;
                        cmdArgs.remove(i + 1);
                        cmdArgs.remove(i);
                        i = -1;
                    }
                } else if (arg.equals(">>") || arg.equals("1>>")) {
                    if (i + 1 < cmdArgs.size()) {
                        outputFile = cmdArgs.get(i + 1);
                        appendOutput = true;
                        cmdArgs.remove(i + 1);
                        cmdArgs.remove(i);
                        i = -1;
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < cmdArgs.size()) {
                        errorFile = cmdArgs.get(i + 1);
                        appendError = false;
                        cmdArgs.remove(i + 1);
                        cmdArgs.remove(i);
                        i = -1;
                    }
                } else if (arg.equals("2>>")) {
                    if (i + 1 < cmdArgs.size()) {
                        errorFile = cmdArgs.get(i + 1);
                        appendError = true;
                        cmdArgs.remove(i + 1);
                        cmdArgs.remove(i);
                        i = -1;
                    }
                }
            }

            if (outputFile != null) {
                try {
                    new File(outputFile).getAbsoluteFile().getParentFile().mkdirs();
                    if (!appendOutput) {
                        new java.io.FileOutputStream(outputFile).close();
                    } else {
                        new java.io.FileOutputStream(outputFile, true).close();
                    }
                } catch (Exception e) {
                }
            }
            if (errorFile != null) {
                try {
                    new File(errorFile).getAbsoluteFile().getParentFile().mkdirs();
                    if (!appendError) {
                        new java.io.FileOutputStream(errorFile).close();
                    } else {
                        new java.io.FileOutputStream(errorFile, true).close();
                    }
                } catch (Exception e) {
                }
            }

            if (command.equals("exit")) {
                break;
            } else if (command.equals("pwd")) {
                if (outputFile != null) {
                    try (java.io.PrintWriter pw =
                            new java.io.PrintWriter(
                                    new java.io.FileWriter(outputFile, appendOutput))) {
                        pw.println(cwd);
                    } catch (Exception e) {
                    }
                } else {
                    System.out.println(cwd);
                }
            } else if (command.equals("jobs")) {
                java.io.PrintWriter outWriter = null;
                if (outputFile != null) {
                    try {
                        outWriter =
                                new java.io.PrintWriter(
                                        new java.io.FileWriter(outputFile, appendOutput));
                    } catch (Exception e) {
                    }
                }

                reapJobs(outWriter, true);

                if (outWriter != null) {
                    outWriter.close();
                }
            } else if (command.equals("cd")) {
                if (cmdArgs.size() < 2) {
                    continue;
                }
                String path = cmdArgs.get(1);

                if (path.equals("~")) {
                    cwd = System.getenv("HOME");
                } else {
                    File dir = new File(path);
                    if (!dir.isAbsolute()) {
                        dir = new File(cwd, path);
                    }

                    if (dir.exists() && dir.isDirectory()) {
                        cwd = dir.getCanonicalPath();
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }
            } else if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < cmdArgs.size(); i++) {
                    sb.append(cmdArgs.get(i));
                    if (i < cmdArgs.size() - 1) {
                        sb.append(" ");
                    }
                }
                if (outputFile != null) {
                    try (java.io.PrintWriter pw =
                            new java.io.PrintWriter(
                                    new java.io.FileWriter(outputFile, appendOutput))) {
                        pw.println(sb.toString());
                    } catch (Exception e) {
                    }
                } else {
                    System.out.println(sb.toString());
                }
            } else if (command.equals("type")) {
                if (cmdArgs.size() < 2) {
                    continue;
                }
                String rem = cmdArgs.get(1);
                String message;

                if (rem.equals("exit")
                        || rem.equals("echo")
                        || rem.equals("type")
                        || rem.equals("pwd")
                        || rem.equals("cd")
                        || rem.equals("jobs")) {

                    message = rem + " is a shell builtin";
                } else {
                    File executable = getExecutable(rem);

                    if (executable != null) {
                        message = rem + " is " + executable.getAbsolutePath();
                    } else {
                        message = rem + ": not found";
                    }
                }

                if (outputFile != null) {
                    try (java.io.PrintWriter pw =
                            new java.io.PrintWriter(
                                    new java.io.FileWriter(outputFile, appendOutput))) {
                        pw.println(message);
                    } catch (Exception e) {
                    }
                } else {
                    System.out.println(message);
                }
            } else {
                File executable = getExecutable(command);
                if (executable != null) {
                    ProcessBuilder pb = new ProcessBuilder(cmdArgs).directory(new File(cwd));

                    if (outputFile != null) {
                        if (appendOutput) {
                            pb.redirectOutput(
                                    ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                        } else {
                            pb.redirectOutput(new File(outputFile));
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (errorFile != null) {
                        if (appendError) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                        } else {
                            pb.redirectError(new File(errorFile));
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();

                    if (isBackground) {
                        int jobId = jobCounter++;
                        jobsList.add(new Job(jobId, process, input, "Running"));
                        System.out.println("[" + jobId + "] " + process.pid());
                    } else {
                        process.waitFor();
                    }
                } else {
                    System.out.println(command + ": command not found");
                }
            }
        }

        scanner.close();
    }
}