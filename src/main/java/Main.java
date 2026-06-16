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
                    System.out.println(command + ": not found");
                }
            }

            else {
               System.out.println(n + ": command not found");
            }
            
         }
    }
}
