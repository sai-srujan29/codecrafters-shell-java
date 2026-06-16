import java.io.File;
import java.util.Scanner;
import java.util.Set;
public class Main {
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);
        Set<String> builtins = Set.of("echo", "exit", "type");

         while (true) { 
            System.out.print("$ ");
            String n = sc.nextLine();

            if (n.equals("exit")) {
                break;
            }

            else if(n.startsWith("echo ")) {
                System.out.println(n.substring(5));
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

                        if(user.exists() && user.canExecute()) {
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
               System.out.println(n + ": command not found");
            }
            
        }
    }
}
