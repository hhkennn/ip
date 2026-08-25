/**
 * Represents a task in Herta's task list.
 *
 * <p>Todo, Deadline, and Event inherit the common completion and description
 * behavior from this class.</p>
 */
public class Task {
    protected String description;
    protected boolean isDone;

    /**
     * Creates a task that is initially not done.
     *
     * @param description the task description.
     */
    public Task(String description) {
        this.description = description;
        this.isDone = false;
    }

    /**
     * Returns the icon used to display this task's completion status.
     *
     * @return {@code X} if the task is done, or a blank space otherwise
     */
    public String getStatusIcon() {
        return isDone ? "X" : " ";
    }

    /**
     * Marks this task as done.
     */
    public void markAsDone() {
        isDone = true;
    }

    /**
     * Marks this task as not done.
     */
    public void markAsNotDone() {
        isDone = false;
    }

    /**
     * Returns whether this task is currently complete.
     *
     * @return {@code true} if the task is complete
     */
    public boolean isDone() {
        return isDone;
    }

    /**
     * Returns this task in the format used by Herta's data file.
     *
     * @return the serialized task text.
     */
    public String toStorageString() {
        return "T | " + getCompletionStatusCode() + " | " + description;
    }

    /**
     * Returns the numeric completion status used in the data file.
     *
     * @return {@code 1} when done, or {@code 0} otherwise
     */
    protected int getCompletionStatusCode() {
        return isDone ? 1 : 0;
    }

    /**
     * Returns the task description.
     *
     * @return the task description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns this task formatted with its completion status and description.
     *
     * @return the formatted task text.
     */
    @Override
    public String toString() {
        return "[" + getStatusIcon() + "] " + description;
    }
}
