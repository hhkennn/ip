package herta.ui;

import herta.task.Task;

/**
 * Collects command output so that it can be returned to a graphical user interface.
 */
public class ResponseCollector implements UiOutput {
    private final StringBuilder output = new StringBuilder();

    /**
     * Adds a message to the collected response.
     *
     * @param message the message to collect
     */
    @Override
    public void showMessage(String message) {
        append(message);
    }

    /**
     * Adds a task to the collected response.
     *
     * @param task the task to collect
     */
    @Override
    public void showTask(Task task) {
        append("  " + task);
    }

    /**
     * Adds the task count to the collected response.
     *
     * @param taskCount the number of tasks
     */
    @Override
    public void showTaskCount(int taskCount) {
        String taskNoun = taskCount == 1 ? "task" : "tasks";
        append("That makes " + taskCount + " " + taskNoun
                + ". Try to keep up.");
    }

    /**
     * Adds Herta's goodbye message to the collected response.
     */
    @Override
    public void showGoodbye() {
        append("Leaving already? Goodbye.");
    }

    /**
     * Returns all collected messages separated by line breaks.
     *
     * @return the collected response
     */
    public String getOutput() {
        return output.toString();
    }

    private void append(String message) {
        if (output.length() > 0) {
            output.append(System.lineSeparator());
        }
        output.append(message);
    }
}
