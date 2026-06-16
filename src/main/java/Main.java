import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {

         Scanner sc = new Scanner(System.in);

         while (true) { 
            System.out.print("$ ");
            String n = sc.nextLine();
            if(n.equals("echo")) {
                System.out.println("n ");;
            }
            System.out.println(n + ": command not found");
         }
    }
}
