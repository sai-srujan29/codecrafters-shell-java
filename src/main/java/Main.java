import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Main {

    static String cwd = System.getProperty("user.dir");
    static final Set<String> BUILTINS = Set.of("exit", "echo", "type", "pwd", "cd", "jobs");
    static final TreeMap<Integer, Job> jobTable = new TreeMap<>();

    record Job(int id, Process proc, String cmd) {}

    static int allocId() {
        for (int i = 1; ; i++) if (!jobTable.containsKey(i)) return i;
    }

    public static void main(String[] args) throws Exception {
        setRawMode(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> setRawMode(false)));
        InputStream stdin = System.in;
        boolean prevWasTab = false;
        outer:
        while (true) {
            reapJobs();
            System.out.print("$ ");
            System.out.flush();
            StringBuilder buf = new StringBuilder();
            while (true) {
                int ch = stdin.read();
                if (ch < 0) break outer;
                if (ch == '\r' || ch == '\n') {
                    System.out.print('\n');
                    System.out.flush();
                    prevWasTab = false;
                    break;
                } else if (ch == '\t') {
                    doTabComplete(buf, prevWasTab);
                    prevWasTab = true;
                    continue;
                } else if (ch == 127 || ch == '\b') {
                    if (buf.length() > 0) {
                        buf.deleteCharAt(buf.length() - 1);
                        System.out.print("\b \b");
                        System.out.flush();
                    }
                } else if (ch == 3) {
                    System.out.print("^C\n");
                    System.out.flush();
                    buf.setLength(0);
                    break;
                } else if (ch == 4) {
                    if (buf.length() == 0) break outer;
                } else if (ch == 27) {
                    int n = stdin.read();
                    if (n == '[') stdin.read();
                } else if (ch >= 32) {
                    buf.append((char) ch);
                    System.out.print((char) ch);
                    System.out.flush();
                }
                prevWasTab = false;
            }
            String line = buf.toString().trim();
            if (line.isEmpty()) continue;
            execute(line);
        }
    }

    static void setRawMode(boolean raw) {
        try {
            ProcessBuilder pb =
                    raw
                            ? new ProcessBuilder(
                                    "stty", "-echo", "-icanon", "min", "1", "time", "0")
                            : new ProcessBuilder("stty", "sane");
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectOutput(new File("/dev/null"));
            pb.redirectError(new File("/dev/null"));
            pb.start().waitFor();
        } catch (Exception ignored) {
        }
    }

    static void doTabComplete(StringBuilder buf, boolean prevWasTab) {
        String line = buf.toString();
        int spaceIdx = line.lastIndexOf(' ');
        String prefix = spaceIdx < 0 ? line : line.substring(spaceIdx + 1);
        String before = spaceIdx < 0 ? "" : line.substring(0, spaceIdx + 1);
        boolean isCommand = before.trim().isEmpty();

        List<String> completions =
                isCommand ? getCommandCompletions(prefix) : getPathCompletions(prefix);

        if (completions.isEmpty()) {
            System.out.print("\007");
            System.out.flush();
            return;
        }

        if (completions.size() == 1) {
            String full = completions.get(0);
            String remaining = full.substring(prefix.length());
            File cf = new File(full);
            if (!cf.isAbsolute()) cf = new File(cwd, full);
            String suffix = remaining + (cf.isDirectory() ? "/" : " ");
            buf.append(suffix);
            System.out.print(suffix);
            System.out.flush();
            return;
        }

        String lcp = longestCommonPrefix(completions);
        if (lcp.length() > prefix.length()) {
            String suffix = lcp.substring(prefix.length());
            buf.append(suffix);
            System.out.print(suffix);
            System.out.flush();
        } else if (prevWasTab) {
            System.out.print('\n');
            for (String c : completions) {
                File cf = new File(c);
                if (!cf.isAbsolute()) cf = new File(cwd, c);
                System.out.print(c + (cf.isDirectory() ? "/" : "") + "  ");
            }
            System.out.print("\n$ " + buf.toString());
            System.out.flush();
        } else {
            System.out.print("\007");
            System.out.flush();
        }
    }

    static List<String> getCommandCompletions(String prefix) {
        TreeSet<String> results = new TreeSet<>();
        for (String b : BUILTINS) {
            if (b.startsWith(prefix)) results.add(b);
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File d = new File(dir);
                if (d.isDirectory()) {
                    String[] files = d.list();
                    if (files != null) {
                        for (String f : files) {
                            if (f.startsWith(prefix) && new File(d, f).canExecute()) results.add(f);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(results);
    }

    static List<String> getPathCompletions(String prefix) {
        List<String> results = new ArrayList<>();
        File base;
        String namePrefix;
        if (prefix.contains("/")) {
            int lastSlash = prefix.lastIndexOf('/');
            String dirPart = prefix.substring(0, lastSlash + 1);
            namePrefix = prefix.substring(lastSlash + 1);
            File dirFile = new File(dirPart);
            base = dirFile.isAbsolute() ? dirFile : new File(cwd, dirPart);
        } else {
            base = new File(cwd);
            namePrefix = prefix;
        }
        String[] files = base.list();
        if (files != null) {
            String dirPart =
                    prefix.contains("/") ? prefix.substring(0, prefix.lastIndexOf('/') + 1) : "";
            for (String f : files) {
                if (f.startsWith(namePrefix)) results.add(dirPart + f);
            }
        }
        Collections.sort(results);
        return results;
    }

    static String longestCommonPrefix(List<String> strs) {
        if (strs.isEmpty()) return "";
        String first = strs.get(0);
        int len = first.length();
        for (String s : strs) {
            len = Math.min(len, s.length());
            for (int i = 0; i < len; i++) {
                if (s.charAt(i) != first.charAt(i)) {
                    len = i;
                    break;
                }
            }
        }
        return first.substring(0, len);
    }

    static void reapJobs() {
        List<Integer> done = new ArrayList<>();
        for (var e : jobTable.entrySet()) if (!e.getValue().proc().isAlive()) done.add(e.getKey());
        Collections.sort(done);
        for (int id : done) {
            Job j = jobTable.remove(id);
            System.out.println("[" + id + "] Done\t\t\t\t" + j.cmd());
            System.out.flush();
        }
    }

    static List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'') {
                i++;
                while (i < s.length() && s.charAt(i) != '\'') cur.append(s.charAt(i++));
                if (i < s.length()) i++;
            } else if (c == '"') {
                i++;
                while (i < s.length() && s.charAt(i) != '"') {
                    if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                        char n = s.charAt(i + 1);
                        if (n == '"' || n == '\\' || n == '$' || n == '`' || n == '\n') {
                            cur.append(n);
                            i += 2;
                        } else {
                            cur.append('\\');
                            i++;
                        }
                    } else cur.append(s.charAt(i++));
                }
                if (i < s.length()) i++;
            } else if (c == '\\') {
                if (i + 1 < s.length()) {
                    cur.append(s.charAt(i + 1));
                    i += 2;
                } else i++;
            } else if (c == ' ' || c == '\t') {
                if (!cur.isEmpty()) {
                    tokens.add(cur.toString());
                    cur = new StringBuilder();
                }
                i++;
            } else if (c == '>') {
                if (!cur.isEmpty()) {
                    tokens.add(cur.toString());
                    cur = new StringBuilder();
                }
                StringBuilder op = new StringBuilder(">");
                i++;
                if (i < s.length() && s.charAt(i) == '>') {
                    op.append('>');
                    i++;
                }
                tokens.add(op.toString());
            } else if ((c == '1' || c == '2')
                    && cur.isEmpty()
                    && i + 1 < s.length()
                    && s.charAt(i + 1) == '>') {
                StringBuilder op = new StringBuilder();
                op.append(c);
                i++;
                op.append('>');
                i++;
                if (i < s.length() && s.charAt(i) == '>') {
                    op.append('>');
                    i++;
                }
                tokens.add(op.toString());
            } else {
                cur.append(c);
                i++;
            }
        }
        if (!cur.isEmpty()) tokens.add(cur.toString());
        return tokens;
    }

    static List<String> splitOnPipe(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\'') {
                cur.append(c);
                i++;
                while (i < line.length() && line.charAt(i) != '\'') cur.append(line.charAt(i++));
                if (i < line.length()) {
                    cur.append(line.charAt(i));
                    i++;
                }
            } else if (c == '"') {
                cur.append(c);
                i++;
                while (i < line.length() && line.charAt(i) != '"') {
                    if (line.charAt(i) == '\\' && i + 1 < line.length()) {
                        cur.append(line.charAt(i));
                        cur.append(line.charAt(i + 1));
                        i += 2;
                    } else cur.append(line.charAt(i++));
                }
                if (i < line.length()) {
                    cur.append(line.charAt(i));
                    i++;
                }
            } else if (c == '\\') {
                cur.append(c);
                if (i + 1 < line.length()) {
                    cur.append(line.charAt(i + 1));
                    i += 2;
                } else i++;
            } else if (c == '|') {
                parts.add(cur.toString());
                cur = new StringBuilder();
                i++;
            } else {
                cur.append(c);
                i++;
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    static class Cmd {
        List<String> args = new ArrayList<>();
        String stdoutFile, stderrFile;
        boolean stdoutAppend, stderrAppend;
    }

    static final Set<String> REDIRECT_OPS = Set.of(">", "1>", ">>", "1>>", "2>", "2>>");

    static Cmd parseCmd(String seg) {
        Cmd cmd = new Cmd();
        List<String> toks = tokenize(seg.trim());
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            if (REDIRECT_OPS.contains(t) && i + 1 < toks.size()) {
                String f = toks.get(i + 1);
                switch (t) {
                    case ">", "1>" -> {
                        cmd.stdoutFile = f;
                        cmd.stdoutAppend = false;
                    }
                    case ">>", "1>>" -> {
                        cmd.stdoutFile = f;
                        cmd.stdoutAppend = true;
                    }
                    case "2>" -> {
                        cmd.stderrFile = f;
                        cmd.stderrAppend = false;
                    }
                    case "2>>" -> {
                        cmd.stderrFile = f;
                        cmd.stderrAppend = true;
                    }
                }
                i++;
            } else {
                cmd.args.add(t);
            }
        }
        return cmd;
    }

    static void execute(String line) throws Exception {
        boolean bg = false;
        String trimmed = line.trim();
        if (trimmed.endsWith("&")) {
            bg = true;
            line = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        List<String> segs = splitOnPipe(line);
        if (segs.size() == 1) {
            Cmd cmd = parseCmd(segs.get(0));
            if (cmd.args.isEmpty()) return;
            runSingle(cmd, bg);
        } else {
            runPipeline(segs);
        }
    }

    static File resolveFile(String path) {
        File f = new File(path);
        if (!f.isAbsolute()) f = new File(cwd, path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        return f;
    }

    static void runSingle(Cmd cmd, boolean bg) throws Exception {
        String name = cmd.args.get(0);
        if (BUILTINS.contains(name)) {
            runBuiltin(cmd, System.out, System.err);
            return;
        }
        String exe = findExe(name);
        if (exe == null) {
            System.err.println(name + ": command not found");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder();
        List<String> fullArgs = new ArrayList<>(cmd.args);
        pb.command(fullArgs);
        pb.directory(new File(cwd));
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

        if (cmd.stdoutFile != null)
            pb.redirectOutput(
                    cmd.stdoutAppend
                            ? ProcessBuilder.Redirect.appendTo(resolveFile(cmd.stdoutFile))
                            : ProcessBuilder.Redirect.to(resolveFile(cmd.stdoutFile)));
        else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        if (cmd.stderrFile != null)
            pb.redirectError(
                    cmd.stderrAppend
                            ? ProcessBuilder.Redirect.appendTo(resolveFile(cmd.stderrFile))
                            : ProcessBuilder.Redirect.to(resolveFile(cmd.stderrFile)));
        else pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process proc = pb.start();
        if (bg) {
            int id = allocId();
            String cmdStr = String.join(" ", cmd.args);
            jobTable.put(id, new Job(id, proc, cmdStr));
            System.out.println("[" + id + "] " + proc.pid());
            System.out.flush();
        } else {
            proc.waitFor();
        }
    }

    static void runBuiltin(Cmd cmd, PrintStream defaultOut, PrintStream defaultErr)
            throws Exception {
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        PrintStream usedOut = defaultOut;
        PrintStream usedErr = defaultErr;

        if (cmd.stdoutFile != null)
            usedOut =
                    new PrintStream(
                            new FileOutputStream(resolveFile(cmd.stdoutFile), cmd.stdoutAppend));
        if (cmd.stderrFile != null)
            usedErr =
                    new PrintStream(
                            new FileOutputStream(resolveFile(cmd.stderrFile), cmd.stderrAppend));

        System.setOut(usedOut);
        System.setErr(usedErr);

        try {
            String name = cmd.args.get(0);
            List<String> args = cmd.args.subList(1, cmd.args.size());
            switch (name) {
                case "exit" -> {
                    int code = args.isEmpty() ? 0 : Integer.parseInt(args.get(0));
                    System.exit(code);
                }
                case "echo" -> System.out.println(String.join(" ", args));
                case "type" -> {
                    for (String a : args) {
                        if (BUILTINS.contains(a)) System.out.println(a + " is a shell builtin");
                        else {
                            String p = findExe(a);
                            if (p != null) System.out.println(a + " is " + p);
                            else System.out.println(a + ": not found");
                        }
                    }
                }
                case "pwd" -> System.out.println(cwd);
                case "cd" -> {
                    String target;
                    String home = System.getenv("HOME");
                    if (home == null) home = System.getProperty("user.home");
                    if (args.isEmpty() || args.get(0).equals("~")) {
                        target = home;
                    } else {
                        target = args.get(0);
                        if (target.startsWith("~/")) target = home + target.substring(1);
                    }
                    Path p = Paths.get(cwd).resolve(target).normalize();
                    if (Files.isDirectory(p)) cwd = p.toString();
                    else System.err.println("cd: " + args.get(0) + ": No such file or directory");
                }
                case "jobs" -> {
                    for (var e : jobTable.entrySet()) {
                        Job j = e.getValue();
                        String status = j.proc().isAlive() ? "Running" : "Done";
                        System.out.println("[" + j.id() + "] " + status + "\t\t\t\t" + j.cmd());
                    }
                }
            }
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
            if (usedOut != defaultOut && usedOut != origOut) usedOut.close();
            if (usedErr != defaultErr && usedErr != origErr) usedErr.close();
        }
    }

    static void runPipeline(List<String> segs) throws Exception {
        List<Cmd> cmds = new ArrayList<>();
        for (String s : segs) {
            Cmd c = parseCmd(s);
            if (!c.args.isEmpty()) cmds.add(c);
        }
        if (cmds.isEmpty()) return;

        byte[] data = null;

        for (int i = 0; i < cmds.size(); i++) {
            Cmd cmd = cmds.get(i);
            boolean isLast = (i == cmds.size() - 1);
            boolean isFirst = (i == 0);

            if (BUILTINS.contains(cmd.args.get(0))) {
                if (isLast) {
                    runBuiltin(cmd, System.out, System.err);
                } else {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    runBuiltin(cmd, ps, System.err);
                    ps.flush();
                    data = baos.toByteArray();
                }
            } else {
                String exe = findExe(cmd.args.get(0));
                if (exe == null) {
                    System.err.println(cmd.args.get(0) + ": command not found");
                    data = new byte[0];
                    continue;
                }

                ProcessBuilder pb = new ProcessBuilder();
                List<String> fullArgs = new ArrayList<>(cmd.args);
                pb.command(fullArgs);
                pb.directory(new File(cwd));

                pb.redirectInput(
                        isFirst ? ProcessBuilder.Redirect.INHERIT : ProcessBuilder.Redirect.PIPE);

                if (isLast) {
                    if (cmd.stdoutFile != null)
                        pb.redirectOutput(
                                cmd.stdoutAppend
                                        ? ProcessBuilder.Redirect.appendTo(
                                                resolveFile(cmd.stdoutFile))
                                        : ProcessBuilder.Redirect.to(resolveFile(cmd.stdoutFile)));
                    else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

                    if (cmd.stderrFile != null)
                        pb.redirectError(
                                cmd.stderrAppend
                                        ? ProcessBuilder.Redirect.appendTo(
                                                resolveFile(cmd.stderrFile))
                                        : ProcessBuilder.Redirect.to(resolveFile(cmd.stderrFile)));
                    else pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process proc = pb.start();

                if (!isFirst && data != null) {
                    final byte[] feedData = data;
                    Thread feeder =
                            new Thread(
                                    () -> {
                                        try (OutputStream os = proc.getOutputStream()) {
                                            os.write(feedData);
                                        } catch (IOException ignored) {
                                        }
                                    });
                    feeder.start();
                    feeder.join();
                }

                if (isLast) {
                    proc.waitFor();
                } else {
                    data = proc.getInputStream().readAllBytes();
                    proc.waitFor();
                }
            }
        }
    }

    static String findExe(String name) {
        if (name.startsWith("/") || name.startsWith("./") || name.startsWith("../")) {
            File f = new File(name);
            if (!f.isAbsolute()) f = new File(cwd, name);
            return (f.exists() && f.canExecute()) ? f.getAbsolutePath() : null;
        }
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            File f = new File(dir, name);
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }
}
