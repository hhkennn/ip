package herta.ui;

import herta.task.Task;
import java.util.Scanner;

/**
 * Handles user interaction for Herta, including console input and output.
 */
public class Ui {
    private static final String INDENT = "     ";
    private static final String SEPARATOR = "____________________________________________________________";

    private final Scanner scanner;

    /**
     * Creates a UI that reads commands from standard input.
     */
    public Ui() {
        scanner = new Scanner(System.in);
    }

    /**
     * Displays Herta's welcome screen.
     */
    public void showWelcome() {
        String banner = " _   _           _\n"
                + "| | | | ___ _ __| |_ __ _\n"
                + "| |_| |/ _ \\ '__| __/ _` |\n"
                + "|  _  |  __/ |  | || (_| |\n"
                + "|_| |_|\\___|_|   \\__\\__,_|\n";
        showSeparator();
        showMessage(banner);
        showMessage("Oh, you're here. I'm Herta.");
        showMessage("Well? What do you want?");
        showSeparator();
    }

    /**
     * Reads one command from standard input.
     *
     * @return the trimmed command, or {@code null} when standard input reaches EOF
     */
    public String readCommand() {
        System.out.print("Your command? ");
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().trim();
    }

    /**
     * Displays the standard separator line.
     */
    public void showSeparator() {
        showMessage(SEPARATOR);
    }

    /**
     * Displays a message with Herta's standard indentation on every line.
     *
     * @param message the message to display
     */
    public void showMessage(String message) {
        String[] lines = message.split("\\R");
        for (String line : lines) {
            System.out.println(INDENT + line);
        }
    }

    /**
     * Displays a task using the indentation used for task details.
     *
     * @param task the task to display
     */
    public void showTask(Task task) {
        showMessage("  " + task);
    }

    /**
     * Displays the task count using the correct singular or plural noun.
     *
     * @param taskCount the number of tasks
     */
    public void showTaskCount(int taskCount) {
        String taskNoun = taskCount == 1 ? "task" : "tasks";
        showMessage("That makes " + taskCount + " " + taskNoun
                + ". Try to keep up.");
    }

    /**
     * Displays Herta's goodbye message and closing separator.
     */
    public void showGoodbye() {
        showMessage("Leaving already? Goodbye.");
        showSeparator();
    }

    /**
     * Closes the input scanner.
     */
    public void close() {
        scanner.close();
    }
}
