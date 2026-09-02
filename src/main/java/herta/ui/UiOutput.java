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
    void showTask(Task task);

    /**
     * Displays the number of tasks to the user.
     *
     * @param taskCount the number of tasks
     */
    void showTaskCount(int taskCount);

    /**
     * Displays Herta's goodbye message.
     */
    void showGoodbye();
}
