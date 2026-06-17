import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        Set<String> builtins = Set.of("echo", "exit", "type", "pwd", "cd");
        String currentDir = System.getProperty("user.dir");

         while (true) { 
            System.out.print("$ ");
            String n = sc.nextLine();

            if (n.startsWith("exit")) {
                break;
            }

            else if(n.startsWith("echo ")) {
                System.out.println(n.substring(5));
            }
            else if (n.equals("pwd")) {
                System.out.println(currentDir);
            }

            else if (n.startsWith("cd ")) {
   
                String directory = n.substring(3);
                
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
            
            else if(n.startsWith("type ")) {
                String command = n.substring(5);
                if(builtins.contains(command)) {
                    System.out.println(command + " is a shell builtin");
                }
                else {
                    String path = System.getenv("PATH");
                    String dirs[] = path.split(":");

                    boolean found = false;

                    for(int i = 0; i < dirs.length; i++) {
                        File user = new File(dirs[i], command);

                        if(user.exists() &&     user.canExecute()) {
                            System.out.println(command + " is " + user.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if(!found) {
                        System.out.println(command + ": not found");
                    }
                }
            }

            else {
                String[] parts = n.split(" ");
                String command = parts[0];

                String path = System.getenv("PATH");
                String[] dirs = path.split(":");

                File executable = null;

                for (int i = 0; i < dirs.length; i++) {
                    File candidate = new File(dirs[i], command);

                    if (candidate.exists() && candidate.canExecute()) {
                        executable = candidate;
                        break;
                    }
                }
                if(executable != null) {
                    ArrayList<String> al = new ArrayList<>();
                    al.add(command);

                    for(int i = 1; i < parts.length; i++) {
                        al.add(parts[i]);
                    }
                    ProcessBuilder pb = new ProcessBuilder(al);
                    pb.inheritIO();

                    Process p = pb.start();
                    p.waitFor();

                }

                else {
                    System.out.println(n + ": command not found");
                }
            }


            
        }
    }
}
