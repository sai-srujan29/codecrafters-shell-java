import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    // Structural job tracking representation
    private static class Job {
        int id;
        Process process;
        String commandLine;

        Job(int id, Process process, String commandLine) {
            this.id = id;
            this.process = process;
            this.commandLine = commandLine;
        }
    }

    private static final ConcurrentHashMap<Integer, Job> activeJobs = new ConcurrentHashMap<>();
    private static final AtomicInteger jobIdSequence = new AtomicInteger(1);

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        while (true) {
            // Reap any finished jobs asynchronously before showing the prompt
            reapCompletedJobs(false);

            System.setOut(originalOut);
            System.setErr(originalErr);
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            // Check if this command should run in the background
            boolean isBackground = false;
            if (input.endsWith("&")) {
                isBackground = true;
                input = input.substring(0, input.length() - 1).trim();
            }

            List<String> tokens = parseArguments(input);
            if (tokens.isEmpty()) {
                continue;
            }

            // --- REDIRECTION HANDLING ---
            String outputFile = null;
            int redirectIndex = -1;
            boolean isStandardOut = true;
            boolean isAppend = false;

            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.equals(">") || token.equals("1>")) {
                    redirectIndex = i;
                    isStandardOut = true;
                    isAppend = false;
                } else if (token.equals(">>") || token.equals("1>>")) {
                    redirectIndex = i;
                    isStandardOut = true;
                    isAppend = true;
                } else if (token.equals("2>")) {
                    redirectIndex = i;
                    isStandardOut = false;
                    isAppend = false;
                } else if (token.equals("2>>")) {
                    redirectIndex = i;
                    isStandardOut = false;
                    isAppend = true;
                }

                if (redirectIndex != -1) {
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                }
            }

            if (redirectIndex != -1) {
                tokens = new ArrayList<>(tokens.subList(0, redirectIndex));
            }

            if (tokens.isEmpty()) {
                continue;
            }

            String command = tokens.get(0);
            FileOutputStream fos = null;

            if (outputFile != null) {
                File file = new File(outputFile);
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                fos = new FileOutputStream(file, isAppend);
                PrintStream ps = new PrintStream(fos);
                if (isStandardOut) System.setOut(ps);
                else System.setErr(ps);
            }

            try {
                // Handle the jobs builtin
                if (command.equals("jobs")) {
                    // Force a collection check on jobs before displaying table listings
                    reapCompletedJobs(false);
                    List<Integer> sortedIds = new ArrayList<>(activeJobs.keySet());
                    Collections.sort(sortedIds);
                    for (int id : sortedIds) {
                        Job j = activeJobs.get(id);
                        if (j != null && j.process.isAlive()) {
                            System.out.println(
                                    "["
                                            + j.id
                                            + "] "
                                            + j.process.pid()
                                            + " Running "
                                            + j.commandLine
                                            + " &");
                        }
                    }
                    continue;
                }

                if (command.equals("exit")) {
                    break;
                }

                if (command.equals("echo")) {
                    List<String> argsList = tokens.subList(1, tokens.size());
                    System.out.println(String.join(" ", argsList));
                    continue;
                }

                if (command.equals("pwd")) {
                    System.out.println(System.getProperty("user.dir"));
                    continue;
                }

                if (command.equals("cd")) {
                    String targetDir = tokens.size() > 1 ? tokens.get(1) : "~";
                    Path path;
                    if (targetDir.equals("~")) {
                        String homeDir = System.getenv("HOME");
                        if (homeDir == null) homeDir = System.getProperty("user.home");
                        path = Paths.get(homeDir);
                    } else {
                        path = Paths.get(targetDir);
                        if (!path.isAbsolute()) {
                            path =
                                    Paths.get(System.getProperty("user.dir"))
                                            .resolve(path)
                                            .normalize();
                        } else {
                            path = path.normalize();
                        }
                    }

                    if (Files.exists(path) && Files.isDirectory(path)) {
                        System.setProperty("user.dir", path.toAbsolutePath().toString());
                    } else {
                        System.err.println("cd: " + targetDir + ": No such file or directory");
                    }
                    continue;
                }

                if (command.equals("type")) {
                    if (tokens.size() < 2) continue;
                    String arg = tokens.get(1);
                    if (arg.equals("echo")
                            || arg.equals("exit")
                            || arg.equals("type")
                            || arg.equals("pwd")
                            || arg.equals("cd")
                            || arg.equals("jobs")) {
                        System.out.println(arg + " is a shell builtin");
                    } else {
                        String executablePath = findInPath(arg);
                        if (executablePath != null) {
                            System.out.println(arg + " is " + executablePath);
                        } else {
                            System.out.println(arg + ": not found");
                        }
                    }
                    continue;
                }

                // Handle external command execution
                String executablePath = findInPath(command);
                if (executablePath != null) {
                    ProcessBuilder pb = new ProcessBuilder(tokens);
                    pb.directory(new File(System.getProperty("user.dir")));

                    if (outputFile != null) {
                        if (fos != null) fos.close();
                        File targetFile = new File(outputFile);
                        ProcessBuilder.Redirect redirectMode =
                                isAppend
                                        ? ProcessBuilder.Redirect.appendTo(targetFile)
                                        : ProcessBuilder.Redirect.to(targetFile);

                        if (isStandardOut) {
                            pb.redirectOutput(redirectMode);
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        } else {
                            pb.redirectError(redirectMode);
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();

                    if (isBackground) {
                        // Allocate the lowest available slot number (Recycle IDs)
                        int nextId = 1;
                        while (activeJobs.containsKey(nextId)) {
                            nextId++;
                        }

                        String displayCmd = String.join(" ", tokens);
                        Job newJob = new Job(nextId, process, displayCmd);
                        activeJobs.put(nextId, newJob);

                        // Match formal POSIX output expectations: [id] pid
                        System.out.println("[" + nextId + "] " + process.pid());
                    } else {
                        // Synchronous process blocks shell
                        process.waitFor();
                    }
                } else {
                    System.err.println(command + ": command not found");
                }
            } finally {
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            }
        }
    }

    private static void reapCompletedJobs(boolean programmaticCall) {
        List<Integer> ids = new ArrayList<>(activeJobs.keySet());
        Collections.sort(ids);
        for (int id : ids) {
            Job j = activeJobs.get(id);
            if (j != null && !j.process.isAlive()) {
                System.out.println(
                        "[" + j.id + "] " + j.process.pid() + " Done " + j.commandLine + " &");
                activeJobs.remove(id);
            }
        }
    }

    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    currentToken.append(input.charAt(i + 1));
                    hasContent = true;
                    i++;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (inDoubleQuotes && c == '\\') {
                if (i + 1 < input.length()) {
                    char nextChar = input.charAt(i + 1);
                    if (nextChar == '"' || nextChar == '\\' || nextChar == '$' || nextChar == '`') {
                        currentToken.append(nextChar);
                        i++;
                    } else {
                        currentToken.append(c);
                    }
                    hasContent = true;
                } else {
                    currentToken.append(c);
                    hasContent = true;
                }
            } else if (inSingleQuotes || inDoubleQuotes) {
                currentToken.append(c);
            } else if (Character.isWhitespace(c)) {
                if (currentToken.length() > 0 || hasContent) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                    hasContent = false;
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0 || hasContent) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] directories = pathEnv.split(File.pathSeparator);
            for (String dir : directories) {
                Path filePath = Paths.get(dir, command);
                if (Files.exists(filePath) && Files.isExecutable(filePath)) {
                    return filePath.toAbsolutePath().toString();
                }
            }
        }
        return null;
    }
}
