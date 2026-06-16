import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {

         Scanner sc = new Scanner(System.in);

         while (true) { 
            System.out.print("$ ");
            String n = sc.nextLine();
            if(n.startsWith("echo")) {
                System.out.println(n.substring(5));
            }
            else {
               System.out.println(n + ": command not found");
            }
            
         }
    }
}
