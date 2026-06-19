import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;

public class Main {
        public static ArrayList<String> parse(String text) {
            ArrayList<String> argsList = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            boolean insideQuotes = false;
            boolean insideDoubleQuotes = false;

            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);

                if(ch == '\\' && insideDoubleQuotes) {
                    if(i+1 < text.length()) {
                        char next = text.charAt(i+1);

                        if(next == '"' || next == '\\') {
                            current.append(next);
                            i++;
                        }
                        else {
                            current.append(ch);
                        }
                    }
                    else {
                        current.append(ch);
                    }
                }

                else if(ch == '\\' && !insideQuotes && !insideDoubleQuotes) {
                    i++;
                    if(i < text.length()) {
                        current.append(text.charAt(i));
                    }
                }

                else if (ch == '\'' && !insideDoubleQuotes) {
                    insideQuotes = !insideQuotes;
                }

                else if(ch == '"' && !insideQuotes) {
                    insideDoubleQuotes = !insideDoubleQuotes;
                }

                else if (ch == ' ' && !insideQuotes && !insideDoubleQuotes) {

                    if (current.length() > 0) {
                        argsList.add(current.toString());
                        current.setLength(0);
                    }

                }
                else {
                current.append(ch);
            }
        }

    if (current.length() > 0) {
        argsList.add(current.toString());
    }

    return argsList;
    }

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");
        String currentDir = System.getProperty("user.dir");


         while (true) { 
            System.out.print("$ ");
            String n = sc.nextLine();
            String command = n;
            String fileName = "";
            String result = "";

            if (n.startsWith("exit")) {
                break;
            }


            if(n.contains("1>")) {
                String parts[] = n.split("1>");
                command = parts[0].trim();
                fileName = parts[1].trim();
            }
                
            else if(n.contains(">")) {
                String parts[] = n.split(">");
                command = parts[0].trim();
                fileName = parts[1].trim();
            }

            if (command.startsWith("echo ")) {

                ArrayList<String> parts = parse(command);
                ArrayList<String> output = new ArrayList<>();

                for (int i = 1; i < parts.size(); i++) {
                    output.add(parts.get(i));
                }

                result = String.join(" ", output);

            }
            else if (command.equals("pwd")) {
                result = currentDir;
            }

            else if (command.startsWith("cd ")) {
   
                String directory = command.substring(3);
                
                if(directory.startsWith("~")) {
                    currentDir = System.getenv("HOME");
                }
                else {
                    File dir;

                if(directory.startsWith("/")) {
                    dir = new File(directory);
                }

                else {
                    dir = new File(currentDir, directory);
                }
                if(dir.exists() && dir.isDirectory()) {
                    currentDir = dir.getCanonicalPath();
                }
                else {
                    System.out.println("cd: " + directory + ": No such file or directory");
                }
            }
                

            }
            
            else if(command.startsWith("type ")) {
                String command2 = command.substring(5);
                if(builtins.contains(command2)) {
                    result = command2 + " is a shell builtin";
                }
                else {
                    String path = System.getenv("PATH");
                    String dirs[] = path.split(":");

                    boolean found = false;

                    for(int i = 0; i < dirs.length; i++) {
                        File user = new File(dirs[i], command2);

                        if(user.exists() && user.canExecute()) {
                            result = command2 + " is " + user.getAbsolutePath();
                            found = true;
                            break;
                        }
                    }

                    if(!found) {
                        result = command2 + ": not found";
                    }
                }
            }

            if (!result.isEmpty()) {

                if (fileName.equals("")) {
                System.out.println(result);
            }

            else {
                FileWriter fw = new FileWriter(fileName);
                fw.write(result);
                fw.write(System.lineSeparator());
                fw.close();
            }
        }

            else if (!command.startsWith("cd ")){
                ArrayList<String> parts = parse(command);
                if (parts.isEmpty()) {
                    continue;
                }
                String command3 = parts.get((0));

                String path = System.getenv("PATH");
                String[] dirs = path.split(":");

                File executable = null;

                for (int i = 0; i < dirs.length; i++) {
                    File candidate = new File(dirs[i], command3);

                    if (candidate.exists() && candidate.canExecute()) {
                        executable = candidate;
                        break;
                    }
                }
                if(executable != null) {
                    ArrayList<String> al = new ArrayList<>();
                    al.add(command3);

                    for(int i = 1; i < parts.size(); i++) {
                        al.add(parts.get(i));
                    }
                    ProcessBuilder pb = new ProcessBuilder(al);
                    if(fileName.isEmpty()) {
                        pb.inheritIO();
                    }
                    else {
                        pb.redirectOutput(new File(fileName));
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }
        
                    Process p = pb.start();
                    p.waitFor();

                }

                else {
                    System.out.println(command + ": command not found");
                }
            }


            
        }
    }
}
