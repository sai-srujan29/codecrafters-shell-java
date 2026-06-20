import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static String currentDirectory = System.getProperty("user.dir");

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] paths = pathEnv.split(File.pathSeparator);

        for (String dir : paths) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private static boolean isBuiltin(String command) {
        return command.equals("echo")
                || command.equals("exit")
                || command.equals("type")
                || command.equals("pwd")
                || command.equals("cd");
    }

    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuote) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (c == '>' && !inSingleQuote && !inDoubleQuote) {

                boolean append = (i + 1 < input.length() && input.charAt(i + 1) == '>');

                if (append) {
                    i++;
                }

                String operator = append ? ">>" : ">";

                if (current.length() > 0) {
                    String prefix = current.toString();

                    if (prefix.equals("1") || prefix.equals("2")) {
                        tokens.add(prefix + operator);
                    } else {
                        tokens.add(prefix);
                        tokens.add(operator);
                    }

                    current.setLength(0);
                } else {
                    tokens.add(operator);
                }

                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static void writeToFile(String fileName, String content) throws Exception {
        Files.writeString(Path.of(fileName), content);
    }

    private static void appendToFile(String fileName, String content) throws Exception {
        Files.writeString(
                Path.of(fileName), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine();
            List<String> parts = parseCommand(input);

            if (parts.isEmpty()) {
                continue;
            }

            String stdoutFile = null;
            String stderrFile = null;

            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> commandParts = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i);

                if ((token.equals(">") || token.equals("1>")) && i + 1 < parts.size()) {

                    stdoutFile = parts.get(i + 1);
                    appendStdout = false;
                    i++;

                } else if ((token.equals(">>") || token.equals("1>>")) && i + 1 < parts.size()) {

                    stdoutFile = parts.get(i + 1);
                    appendStdout = true;
                    i++;

                } else if (token.equals("2>") && i + 1 < parts.size()) {

                    stderrFile = parts.get(i + 1);
                    appendStderr = false;
                    i++;

                } else if (token.equals("2>>") && i + 1 < parts.size()) {

                    stderrFile = parts.get(i + 1);
                    appendStderr = true;
                    i++;

                } else {
                    commandParts.add(token);
                }
            }

            if (commandParts.isEmpty()) {
                continue;
            }

            String commandName = commandParts.get(0);

            if (commandName.equals("exit")) {
                break;
            }

            if (commandName.equals("pwd")) {
                String result = currentDirectory + System.lineSeparator();

                if (stdoutFile != null) {
                    if (appendStdout) {
                        appendToFile(stdoutFile, result);
                    } else {
                        writeToFile(stdoutFile, result);
                    }
                } else {
                    System.out.print(result);
                }

                if (stderrFile != null) {
                    if (appendStderr) {
                        appendToFile(stderrFile, "");
                    } else {
                        writeToFile(stderrFile, "");
                    }
                }

                continue;
            }

            if (commandName.equals("cd")) {
                if (commandParts.size() < 2) {
                    continue;
                }

                String path = commandParts.get(1);
                File dir;

                if (path.equals("~")) {
                    dir = new File(System.getenv("HOME"));
                } else if (path.startsWith("/")) {
                    dir = new File(path);
                } else {
                    dir = new File(currentDirectory, path);
                }

                if (dir.exists() && dir.isDirectory()) {
                    currentDirectory = dir.getCanonicalPath();
                } else {
                    String error =
                            "cd: " + path + ": No such file or directory" + System.lineSeparator();

                    if (stderrFile != null) {
                        if (appendStderr) {
                            appendToFile(stderrFile, error);
                        } else {
                            writeToFile(stderrFile, error);
                        }
                    } else {
                        System.err.print(error);
                    }
                }

                continue;
            }

            if (commandName.equals("echo")) {
                StringBuilder output = new StringBuilder();

                for (int i = 1; i < commandParts.size(); i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(commandParts.get(i));
                }

                output.append(System.lineSeparator());

                if (stdoutFile != null) {
                    if (appendStdout) {
                        appendToFile(stdoutFile, output.toString());
                    } else {
                        writeToFile(stdoutFile, output.toString());
                    }
                } else {
                    System.out.print(output);
                }

                if (stderrFile != null) {
                    if (appendStderr) {
                        appendToFile(stderrFile, "");
                    } else {
                        writeToFile(stderrFile, "");
                    }
                }

                continue;
            }

            if (commandName.equals("type")) {
                if (commandParts.size() < 2) {
                    continue;
                }

                String command = commandParts.get(1);
                String result;

                if (isBuiltin(command)) {
                    result = command + " is a shell builtin";
                } else {
                    String executable = findExecutable(command);

                    if (executable != null) {
                        result = command + " is " + executable;
                    } else {
                        result = command + ": not found";
                    }
                }

                result += System.lineSeparator();

                if (stdoutFile != null) {
                    if (appendStdout) {
                        appendToFile(stdoutFile, result);
                    } else {
                        writeToFile(stdoutFile, result);
                    }
                } else {
                    System.out.print(result);
                }

                if (stderrFile != null) {
                    if (appendStderr) {
                        appendToFile(stderrFile, "");
                    } else {
                        writeToFile(stderrFile, "");
                    }
                }

                continue;
            }

            String executable = findExecutable(commandName);

            if (executable != null) {
                ProcessBuilder pb = new ProcessBuilder(commandParts);
                pb.directory(new File(currentDirectory));

                if (stdoutFile != null) {
                    if (appendStdout) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(stdoutFile)));
                    } else {
                        pb.redirectOutput(new File(stdoutFile));
                    }
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }

                if (stderrFile != null) {
                    if (appendStderr) {
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(stderrFile)));
                    } else {
                        pb.redirectError(new File(stderrFile));
                    }
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = pb.start();
                process.waitFor();

            } else {
                String error = commandName + ": command not found" + System.lineSeparator();

                if (stderrFile != null) {
                    if (appendStderr) {
                        appendToFile(stderrFile, error);
                    } else {
                        writeToFile(stderrFile, error);
                    }
                } else {
                    System.err.print(error);
                }
            }
        }

        scanner.close();
    }
}