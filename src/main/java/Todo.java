/**
 * Represents a task that has no date or time attached to it.
 */
public class Todo extends Task {

    /**
     * Creates an incomplete todo task.
     *
     * @param description the task description.
     */
    public Todo(String description) {
        super(description);
    }

    /**
     * Returns this todo formatted with its task type, completion status and description.
     *
     * @return the formatted todo text.
     */
    @Override
    public String toString() {
        return "[T]" + super.toString();
    }
}
