package herta.ui;

import herta.task.Task;

/**
 * Provides output operations used by Herta commands.
 */
public interface UiOutput {

    /**
     * Displays a message to the user.
     *
     * @param message the message to display
     */
    void showMessage(String message);

    /**
     * Displays a task to the user.
     *
     * @param task the task to display
     */
    default void showTask(Task task) {
        showMessage("  " + task);
    }

    /**
     * Displays a task with its one-based list number.
     *
     * @param taskNumber the one-based number shown to the user
     * @param task the task to display
     */
    default void showTask(int taskNumber, Task task) {
        showMessage(taskNumber + "." + task);
    }

    /**
     * Displays the number of tasks to the user.
     *
     * @param taskCount the number of tasks
     */
    default void showTaskCount(int taskCount) {
        String taskNoun = taskCount == 1 ? "task" : "tasks";
        showMessage("That makes " + taskCount + " " + taskNoun
                + ". Try to keep up.");
    }

    /**
     * Displays Herta's goodbye message.
     */
    void showGoodbye();
}
