import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Herta {
    private static final String INDENT = "     ";
    private static final String SEPARATOR = "____________________________________________________________";
    private static final String TODO_COMMAND = "todo ";
    private static final String DEADLINE_COMMAND = "deadline ";
    private static final String EVENT_COMMAND = "event ";
    private static final String DELETE_COMMAND = "delete ";

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

    /**
     * Prints a task using the indentation used for task details.
     *
     * @param task the task to print
     */
    private static void printTask(Task task) {
        printIndented("  " + task);
    }

    public static void main(String[] args) {
        String banner = " _   _           _\n"
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
            System.out.print("Enter a command: ");
            if (!scanner.hasNextLine()) {
                printIndented(SEPARATOR);
                printIndented("Bye. Hope to see you again soon!");
                printIndented(SEPARATOR);
                break;
            }
            String input = scanner.nextLine().trim();
            printIndented(SEPARATOR);

            try {
                if (input.equals("bye")) {
                    printIndented("Bye. Hope to see you again soon!");
                    printIndented(SEPARATOR);
                    break;
                } else if (input.equals("list")) {
                    printIndented("Here are the tasks in your list:");
                    for (int i = 0; i < tasks.size(); i++) {
                        printIndented((i + 1) + "." + tasks.get(i));
                    }
                } else if (input.equals("mark") || input.startsWith("mark ")) {
                    markTask(tasks, input);
                } else if (input.equals("unmark") || input.startsWith("unmark ")) {
                    unmarkTask(tasks, input);
                } else if (input.equals("delete") || input.startsWith(DELETE_COMMAND)) {
                    deleteTask(tasks, input);
                } else if (input.equals("todo") || input.startsWith(TODO_COMMAND)) {
                    addTodo(tasks, input);
                } else if (input.equals("deadline") || input.startsWith(DEADLINE_COMMAND)) {
                    addDeadline(tasks, input);
                } else if (input.equals("event") || input.startsWith(EVENT_COMMAND)) {
                    addEvent(tasks, input);
                } else if (input.isEmpty()) {
                    throw new HertaException("Please enter a command, such as: todo read book.");
                } else {
                    throw new HertaException("I don't recognise that command :(\n" +
                            "Try todo, deadline, event, list, mark, unmark, delete, and bye.");
                }
            } catch (HertaException e) {
                printIndented("Oops! " + e.getMessage());
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
        printTask(task);
        printTaskCount(tasks);
    }

    /**
     * Prints the number of tasks using the correct singular or plural noun.
     *
     * @param tasks the task list to count
     */
    private static void printTaskCount(List<Task> tasks) {
        String taskNoun = tasks.size() == 1 ? "task" : "tasks";
        printIndented("Now you have " + tasks.size() + " " + taskNoun + " in the list.");
    }

    /**
     * Parses and adds a todo command's description.
     *
     * @param tasks the task list to update
     * @param input the complete todo command entered by the user
     * @throws HertaException if the todo description is empty
     */
    private static void addTodo(List<Task> tasks, String input) throws HertaException {
        String description = input.substring("todo".length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("A todo description cannot be empty. Try: todo <description>.");
        }
        addTask(tasks, new Todo(description));
    }

    /**
     * Parses and adds a deadline command's description and date/time.
     *
     * @param tasks the task list to update
     * @param input the complete deadline command entered by the user
     */
    private static void addDeadline(List<Task> tasks, String input) throws HertaException {
        String content = input.substring("deadline".length()).trim();
        String[] deadlineParts = content.split("\\s+/by\\s+", 2);
        if (deadlineParts.length != 2) {
            throw new HertaException("A deadline must look like: deadline <description> /by <date/time>.");
        }

        String description = deadlineParts[0].trim();
        String by = deadlineParts[1].trim();
        if (description.isEmpty() || by.isEmpty()) {
            throw new HertaException("A deadline must look like: deadline <description> /by <date/time>.");
        }

        addTask(tasks, new Deadline(description, by));
    }

    /**
     * Parses and adds an event command's description, start, and end date/time.
     *
     * @param tasks the task list to update
     * @param input the complete event command entered by the user
     */
    private static void addEvent(List<Task> tasks, String input) throws HertaException {
        String content = input.substring("event".length()).trim();
        String[] eventParts = content.split("\\s+/from\\s+", 2);
        if (eventParts.length != 2) {
            throw new HertaException("An event must look like: event <description> /from <start> /to <end>.");
        }

        String[] timeParts = eventParts[1].split("\\s+/to\\s+", 2);
        if (timeParts.length != 2) {
            throw new HertaException("An event must look like: event <description> /from <start> /to <end>.");
        }

        String description = eventParts[0].trim();
        String from = timeParts[0].trim();
        String to = timeParts[1].trim();
        if (description.isEmpty() || from.isEmpty() || to.isEmpty()) {
            throw new HertaException("An event must look like: event <description> /from <start> /to <end>.");
        }

        addTask(tasks, new Event(description, from, to));
    }

    /**
     * Deletes the requested task and prints the standard confirmation message.
     *
     * @param tasks the task list to update
     * @param input the complete delete command entered by the user
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void deleteTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("delete".length()).trim();
        Task task = getTask(tasks, taskNumber, "delete");
        tasks.remove(task);
        printIndented("Noted. I've removed this task:");
        printTask(task);
        printTaskCount(tasks);
    }

    /**
     * Marks the requested task as done.
     *
     * @param tasks the task list to update
     * @param input the complete mark command entered by the user
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void markTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("mark".length()).trim();
        Task task = getTask(tasks, taskNumber, "mark");
        task.markAsDone();
        printIndented("Nice! I've marked this task as done:");
        printTask(task);
    }

    /**
     * Marks the requested task as not done.
     *
     * @param tasks the task list to update
     * @param input the complete unmark command entered by the user
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void unmarkTask(List<Task> tasks, String input) throws HertaException {
        String taskNumber = input.substring("unmark".length()).trim();
        Task task = getTask(tasks, taskNumber, "unmark");
        task.markAsNotDone();
        printIndented("OK, I've marked this task as not done yet:");
        printTask(task);
    }

    /**
     * Finds a task using the one-based number shown by the list command.
     *
     * @param tasks the task list to search
     * @param taskNumber the task number entered by the user
     * @param command the command used to request the task
     * @return the requested task
     * @throws HertaException if the task number is invalid or out of range
     */
    private static Task getTask(List<Task> tasks, String taskNumber, String command) throws HertaException {
        final int taskIndex;
        try {
            taskIndex = Integer.parseInt(taskNumber) - 1;
        } catch (NumberFormatException e) {
            throw new HertaException("Please provide a valid task number. Try: " + command + " 1.");
        }

        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            throw new HertaException("That task number is not in your list. Try other task numbers");
        }
        return tasks.get(taskIndex);
    }
}
