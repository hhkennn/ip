import java.util.Scanner;

public class Herta {
    private static final String INDENT = "     ";
    private static final String SEPARATOR = "____________________________________________________________";

    /**
     * Prints each line of chatbot output with the standard indentation.
     *
     * @param message the message to print
     */
    private static void printIndented(String message) {
        // Split message wherever a line break occurs
        String[] lines = message.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            System.out.println(INDENT + lines[i]);
        }
    }

    public static void main(String[] args) {
        String banner = " _   _           _        \n"
                + "| | | | ___ _ __| |_ __ _\n"
                + "| |_| |/ _ \\ '__| __/ _` |\n"
                + "|  _  |  __/ |  | || (_| |\n"
                + "|_| |_|\\___|_|   \\__\\__,_|\n";
        printIndented(SEPARATOR);
        printIndented(banner);
        printIndented("Hello! I'm Herta.");
        printIndented("What can I do for you?");
        printIndented(SEPARATOR);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter a command ('bye' to exit): ");
            String input = scanner.nextLine();
            printIndented(SEPARATOR);

            if (input.equals("bye")) {
                printIndented("Bye. Hope to see you again soon!");
                printIndented(SEPARATOR);
                break;
            }

            printIndented(input);
            printIndented(SEPARATOR);
        }

        scanner.close();
    }
}
