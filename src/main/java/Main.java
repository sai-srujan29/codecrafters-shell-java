import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    static Process backgroundProcess = null;
    static String backgroundCommand = null;

    static ArrayList<String> parseCommand(String command) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideSingleQuotes = false;
        boolean insideDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);

            if (ch == '\\' && insideDoubleQuotes) {
                if (i + 1 < command.length()) {
                    char next = command.charAt(i + 1);
                    if (next == '\\' || next == '"') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                current.append(ch);
                continue;
            }

            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && !insideSingleQuotes && !insideDoubleQuotes) {
                escaped = true;
                continue;
            }
            if (ch == '\'' && !insideDoubleQuotes) {
                insideSingleQuotes = !insideSingleQuotes;
                continue;
            }
            if (ch == '"' && !insideSingleQuotes) {
                insideDoubleQuotes = !insideDoubleQuotes;
                continue;
            }
            if (ch == ' ' && !insideSingleQuotes && !insideDoubleQuotes) {

                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }

            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {

            System.out.print("$ ");
            String command = scanner.nextLine();

            String errorFile = null;
            String outputFile = null;
            boolean appendMode = false;
            boolean errorAppendMode = false;

            if (command.contains("2>>")) {
                String[] redir = command.split("2>>", 2);
                command = redir[0].trim();
                errorFile = redir[1].trim();
                errorAppendMode = true;
            } else if (command.contains("1>>")) {
                String[] redir = command.split("1>>", 2);
                command = redir[0].trim();
                outputFile = redir[1].trim();
                appendMode = true;
            } else if (command.contains(">>")) {
                String[] redir = command.split(">>", 2);
                command = redir[0].trim();
                outputFile = redir[1].trim();
                appendMode = true;
            } else if (command.contains("2>")) {
                String[] redir = command.split("2>", 2);
                command = redir[0].trim();
                errorFile = redir[1].trim();
            } else if (command.contains("1>")) {
                String[] redir = command.split("1>", 2);
                command = redir[0].trim();
                outputFile = redir[1].trim();
            } else if (command.contains(">")) {
                String[] redir = command.split(">", 2);
                command = redir[0].trim();
                outputFile = redir[1].trim();
            }

            if (command.equals("exit")) {
                break;
            }
            if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
                continue;
            }
            if (command.equals("jobs")) {
                if (backgroundProcess != null && backgroundProcess.isAlive()) {
                    System.out.printf("[1]+ Running%-17s%s%n", "", backgroundCommand);
                }
                continue;
            }
            if (command.startsWith("cd")) {
                String path = command.substring(3);
                if (path.equals("~")) {
                    currentDirectory = new File(System.getenv("HOME"));
                    continue;
                }

                File newDir;
                if (path.startsWith("/")) {
                    newDir = new File(path);
                } else {
                    newDir = new File(currentDirectory, path);
                }
                if (newDir.exists() && newDir.isDirectory()) {
                    try {
                        currentDirectory = newDir.getCanonicalFile();
                    } catch (Exception e) {
                        currentDirectory = newDir;
                    }
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
                continue;
            }

            if (command.startsWith("echo")) {
                ArrayList<String> argsList = parseCommand(command);
                argsList.remove(0);

                String result = String.join(" ", argsList);

                if (outputFile != null) {
                    if (appendMode) {
                        java.io.FileWriter fw = new java.io.FileWriter(outputFile, true);
                        fw.write(result + System.lineSeparator());
                        fw.close();
                    } else {
                        java.io.PrintWriter pw = new java.io.PrintWriter(outputFile);
                        pw.println(result);
                        pw.close();
                    }
                } else {
                    System.out.println(result);
                }
                if (errorFile != null) {
                    if (errorAppendMode) {
                        new java.io.FileWriter(errorFile, true).close();
                    } else {
                        new java.io.PrintWriter(errorFile).close();
                    }
                }

                continue;
            }

            if (command.startsWith("type")) {

                String arg = command.substring(5);

                if (arg.equals("echo")
                        || arg.equals("exit")
                        || arg.equals("type")
                        || arg.equals("pwd")
                        || arg.equals("cd")
                        || arg.equals("jobs")) {
                    System.out.println(arg + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] dirs = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : dirs) {

                    File file = new File(dir, arg);

                    if (file.exists() && file.canExecute()) {
                        System.out.println(arg + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(arg + ": not found");
                }

                continue;
            }

            ArrayList<String> partsList = parseCommand(command);
            String[] parts = partsList.toArray(new String[0]);
            String cmd = parts[0];

            boolean backgroundJob = false;
            if (partsList.size() > 0 && partsList.get(partsList.size() - 1).equals("&")) {
                backgroundJob = true;
                partsList.remove(partsList.size() - 1);

                parts = partsList.toArray(new String[0]);

                cmd = parts[0];
            }

            String pathEnv = System.getenv("PATH");
            String[] dirs = pathEnv.split(File.pathSeparator);

            boolean executed = false;

            for (String dir : dirs) {

                File file = new File(dir, cmd);

                if (file.exists() && file.canExecute()) {

                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.environment().put("PATH", System.getenv("PATH"));

                    if (outputFile != null) {
                        if (appendMode) {
                            pb.redirectOutput(
                                    ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                        } else {
                            pb.redirectOutput(new File(outputFile));
                        }
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (errorFile != null) {
                        if (errorAppendMode) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                        } else {
                            pb.redirectError(new File(errorFile));
                        }
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();
                    if (backgroundJob) {
                        backgroundProcess = process;
                        backgroundCommand = command;
                        System.out.println("[1] " + process.pid());
                    } else {
                        process.waitFor();
                    }
                    executed = true;
                    break;
                }
            }

            if (executed) {
                continue;
            }

            System.out.println(command + ": command not found");
        }
    }
}