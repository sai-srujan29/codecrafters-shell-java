import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {

    static final Set<String> BUILTINS = Set.of("echo", "exit", "type", "pwd", "cd", "jobs");

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            System.out.flush();
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            run(tokenize(line));
        }
    }

    record Job(int num, long pid, String cmd, Process proc) {}

    static final List<Job> jobs = new ArrayList<>();
    static int jobCounter = 0;

    static void run(String[] tokens) throws Exception {
        // Check for background operator
        boolean background = tokens.length > 0 && tokens[tokens.length - 1].equals("&");
        if (background) tokens = Arrays.copyOf(tokens, tokens.length - 1);
        // Parse redirects
        List<String> argList = new ArrayList<>();
        String outFile = null;
        boolean outAppend = false;
        String errFile = null;
        boolean errAppend = false;
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if ((t.equals(">") || t.equals("1>")) && i + 1 < tokens.length) {
                outFile = tokens[++i];
                outAppend = false;
            } else if ((t.equals(">>") || t.equals("1>>")) && i + 1 < tokens.length) {
                outFile = tokens[++i];
                outAppend = true;
            } else if (t.equals("2>") && i + 1 < tokens.length) {
                errFile = tokens[++i];
                errAppend = false;
            } else if (t.equals("2>>") && i + 1 < tokens.length) {
                errFile = tokens[++i];
                errAppend = true;
            } else argList.add(t);
        }
        if (argList.isEmpty()) return;
        String[] parts = argList.toArray(new String[0]);
        String cmd = parts[0];

        PrintStream out = System.out;
        PrintStream err = System.err;
        FileOutputStream fout = null, ferr = null;
        if (outFile != null) {
            fout = new FileOutputStream(outFile, outAppend);
            out = new PrintStream(fout);
        }
        if (errFile != null) {
            ferr = new FileOutputStream(errFile, errAppend);
            err = new PrintStream(ferr);
        }

        try {
            switch (cmd) {
                case "exit" -> System.exit(parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
                case "echo" ->
                        out.println(String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)));
                case "pwd" -> out.println(System.getProperty("user.dir"));
                case "jobs" -> {
                    int last = jobs.size() - 1;
                    for (int i = 0; i < jobs.size(); i++) {
                        Job j = jobs.get(i);
                        char marker = i == last ? '+' : i == last - 1 ? '-' : ' ';
                        String status = j.proc().isAlive() ? "Running" : "Done";
                        out.printf("[%d]%c  %-24s%s &%n", j.num(), marker, status, j.cmd());
                    }
                }
                case "type" -> {
                    if (parts.length < 2) {
                        out.println("type: missing argument");
                        break;
                    }
                    String name = parts[1];
                    if (BUILTINS.contains(name)) out.println(name + " is a shell builtin");
                    else {
                        String path = findExec(name);
                        if (path != null) out.println(name + " is " + path);
                        else out.println(name + ": not found");
                    }
                }
                case "cd" -> {
                    String target =
                            parts.length < 2 || parts[1].equals("~")
                                    ? System.getenv("HOME")
                                    : parts[1];
                    if (target == null) {
                        err.println("cd: HOME not set");
                        break;
                    }
                    File dir =
                            new File(target).isAbsolute()
                                    ? new File(target)
                                    : new File(System.getProperty("user.dir"), target);
                    if (dir.exists() && dir.isDirectory())
                        System.setProperty("user.dir", dir.getCanonicalPath());
                    else err.println("cd: " + target + ": No such file or directory");
                }
                default -> {
                    String path = findExec(cmd);
                    if (path == null) {
                        err.println(cmd + ": command not found");
                        break;
                    }
                    List<String> shCmd =
                            new ArrayList<>(
                                    List.of(
                                            "/bin/sh",
                                            "-c",
                                            buildShellCmd(
                                                    path, parts, outFile, outAppend, errFile,
                                                    errAppend)));
                    ProcessBuilder pb = new ProcessBuilder(shCmd);
                    pb.redirectOutput(
                            outFile != null
                                    ? ProcessBuilder.Redirect.PIPE
                                    : ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(
                            errFile != null
                                    ? ProcessBuilder.Redirect.PIPE
                                    : ProcessBuilder.Redirect.INHERIT);
                    pb.directory(new File(System.getProperty("user.dir")));
                    Process proc = pb.start();
                    if (background) {
                        int jobNum = ++jobCounter;
                        long pid = proc.pid();
                        String cmdStr = String.join(" ", parts);
                        jobs.add(new Job(jobNum, pid, cmdStr, proc));
                        System.out.println("[" + jobNum + "] " + pid);
                        // Don't wait — shell returns to prompt immediately
                    } else {
                        proc.waitFor();
                    }
                }
            }
        } finally {
            if (fout != null) {
                out.flush();
                fout.close();
            }
            if (ferr != null) {
                err.flush();
                ferr.close();
            }
        }
    }

    static String buildShellCmd(
            String path,
            String[] parts,
            String outFile,
            boolean outAppend,
            String errFile,
            boolean errAppend) {
        StringBuilder sb =
                new StringBuilder("exec -a ").append(q(parts[0])).append(" ").append(q(path));
        for (int i = 1; i < parts.length; i++) sb.append(" ").append(q(parts[i]));
        if (outFile != null) sb.append(outAppend ? " >>" : " >").append(q(outFile));
        if (errFile != null) sb.append(errAppend ? " 2>>" : " 2>").append(q(errFile));
        return sb.toString();
    }

    static String findExec(String cmd) {
        File f = new File(cmd);
        if (f.isAbsolute() && f.isFile() && f.canExecute()) return f.getAbsolutePath();
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File candidate = new File(dir, cmd);
            if (candidate.isFile() && candidate.canExecute()) return candidate.getAbsolutePath();
        }
        return null;
    }

    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, hasContent = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inSingle) {
                if (c == '\'') inSingle = false;
                else {
                    cur.append(c);
                    hasContent = true;
                }
            } else if (inDouble) {
                if (c == '"') inDouble = false;
                else if (c == '\\' && i + 1 < line.length()) {
                    char n = line.charAt(i + 1);
                    cur.append((n == '"' || n == '\\' || n == '$' || n == '\n') ? n : c);
                    if (n == '"' || n == '\\' || n == '$' || n == '\n') i++;
                    hasContent = true;
                } else {
                    cur.append(c);
                    hasContent = true;
                }
            } else {
                if (c == '\\' && i + 1 < line.length()) {
                    cur.append(line.charAt(++i));
                    hasContent = true;
                } else if (c == '\'') {
                    inSingle = true;
                    hasContent = true;
                } else if (c == '"') {
                    inDouble = true;
                    hasContent = true;
                } else if (c == ' ' || c == '\t') {
                    if (hasContent) {
                        tokens.add(cur.toString());
                        cur.setLength(0);
                        hasContent = false;
                    }
                } else {
                    cur.append(c);
                    hasContent = true;
                }
            }
        }
        if (hasContent) tokens.add(cur.toString());
        return tokens.toArray(new String[0]);
    }
}