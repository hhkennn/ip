import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Herta {
    private static final String INDENT = "     ";
    private static final String SEPARATOR = "____________________________________________________________";
    private static final String TODO_COMMAND = "todo ";
    private static final String DEADLINE_COMMAND = "deadline ";
    private static final String EVENT_COMMAND = "event ";

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
        List<Task> tasks = new ArrayList<>();

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
                    printIndented((i + 1) + "." + tasks.get(i));
                }
            } else if (input.startsWith("mark ")) {
                String taskNumber = input.substring("mark ".length()).trim();
                try {
                    int taskIndex = Integer.parseInt(taskNumber) - 1;
                    if (taskIndex < 0 || taskIndex >= tasks.size()) {
                        printIndented("That task number is not in your list.");
                    } else {
                        Task task = tasks.get(taskIndex);
                        task.markAsDone();
                        printIndented("Nice! I've marked this task as done:");
                        printIndented("  " + task);
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
                        Task task = tasks.get(taskIndex);
                        task.markAsNotDone();
                        printIndented("OK, I've marked this task as not done yet:");
                        printIndented("  " + task);
                    }
                } catch (NumberFormatException e) {
                    printIndented("Please provide a valid task number.");
                }
            } else if (input.startsWith(TODO_COMMAND)) {
                String description = input.substring(TODO_COMMAND.length()).trim();
                if (description.isEmpty()) {
                    printIndented("Please enter a task description.");
                } else {
                    addTask(tasks, new Todo(description));
                }
            } else if (input.startsWith(DEADLINE_COMMAND)) {
                addDeadline(tasks, input.substring(DEADLINE_COMMAND.length()).trim());
            } else if (input.startsWith(EVENT_COMMAND)) {
                addEvent(tasks, input.substring(EVENT_COMMAND.length()).trim());
            } else if (input.isEmpty()) {
                printIndented("Please enter a task.");
            } else {
                // Keep accepting the original bare-task syntax as a todo for backwards compatibility.
                addTask(tasks, new Todo(input));
            }

            printIndented(SEPARATOR);
        }

        scanner.close();
    }

    /**
     * Adds a task and prints the standard confirmation message.
     *
     * @param tasks the task list to update
     * @param task the task to add
     */
    private static void addTask(List<Task> tasks, Task task) {
        tasks.add(task);
        printIndented("Got it. I've added this task:");
        printIndented("  " + task);
        printIndented("Now you have " + tasks.size() + " tasks in the list.");
    }

    /**
     * Parses and adds a deadline command's description and date/time.
     *
     * @param tasks the task list to update
     * @param content the text after the {@code deadline} command
     */
    private static void addDeadline(List<Task> tasks, String content) {
        int byIndex = content.indexOf("/by");
        if (byIndex <= 0) {
            printIndented("Please use: deadline <description> /by <date/time>.");
            return;
        }

        String description = content.substring(0, byIndex).trim();
        String by = content.substring(byIndex + 3).trim();
        if (description.isEmpty() || by.isEmpty()) {
            printIndented("Please use: deadline <description> /by <date/time>.");
            return;
        }

        addTask(tasks, new Deadline(description, by));
    }

    /**
     * Parses and adds an event command's description, start, and end date/time.
     *
     * @param tasks the task list to update
     * @param content the text after the {@code event} command
     */
    private static void addEvent(List<Task> tasks, String content) {
        int fromIndex = content.indexOf("/from");
        int toIndex = content.indexOf("/to", fromIndex + 5);
        if (fromIndex <= 0 || toIndex <= fromIndex + 5) {
            printIndented("Please use: event <description> /from <start> /to <end>.");
            return;
        }

        String description = content.substring(0, fromIndex).trim();
        String from = content.substring(fromIndex + 5, toIndex).trim();
        String to = content.substring(toIndex + 3).trim();
        if (description.isEmpty() || from.isEmpty() || to.isEmpty()) {
            printIndented("Please use: event <description> /from <start> /to <end>.");
            return;
        }

        addTask(tasks, new Event(description, from, to));
    }
}
