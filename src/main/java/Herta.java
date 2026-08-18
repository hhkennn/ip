import java.util.ArrayList;
import java.util.List;
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
        List<String> tasks = new ArrayList<>();
        List<Boolean> completedTasks = new ArrayList<>();

        while (true) {
            System.out.print("Enter a command ('bye' to exit): ");
            String input = scanner.nextLine();
            printIndented(SEPARATOR);

            if (input.equals("bye")) {
                printIndented("Bye. Hope to see you again soon!");
                printIndented(SEPARATOR);
                break;
            } else if (input.equals("list")) {
                printIndented("Here are the tasks in your list:");
                for (int i = 0; i < tasks.size(); i++) {
                    String status = completedTasks.get(i) ? "X" : " ";
                    printIndented((i + 1) + ".[" + status + "] " + tasks.get(i));
                }
            } else if (input.startsWith("mark ")) {
                String taskNumber = input.substring("mark ".length()).trim();
                try {
                    int taskIndex = Integer.parseInt(taskNumber) - 1;
                    if (taskIndex < 0 || taskIndex >= tasks.size()) {
                        printIndented("That task number is not in your list.");
                    } else {
                        completedTasks.set(taskIndex, true);
                        printIndented("Nice! I've marked this task as done:");
                        printIndented("  [X] " + tasks.get(taskIndex));
                    }
                } catch (NumberFormatException e) {
                    printIndented("Please provide a valid task number.");
                }
            } else if (input.startsWith("unmark ")) {
                String taskNumber = input.substring("unmark ".length()).trim();
                try {
                    int taskIndex = Integer.parseInt(taskNumber) - 1;
                    if (taskIndex < 0 || taskIndex >= tasks.size()) {
                        printIndented("That task number is not in your list.");
                    } else {
                        completedTasks.set(taskIndex, false);
                        printIndented("OK, I've marked this task as not done yet:");
                        printIndented("  [ ] " + tasks.get(taskIndex));
                    }
                } catch (NumberFormatException e) {
                    printIndented("Please provide a valid task number.");
                }
            } else if (input.isEmpty()) {
                printIndented("Please enter a task.");
            } else {
                tasks.add(input);
                completedTasks.add(false);
                printIndented("added: " + input);
            }

            printIndented(SEPARATOR);
        }

        scanner.close();
    }
}
