/**
 * Represents a task that has no date or time attached to it.
 */
public class Todo extends Task {

    /**
     * Creates an incomplete todo task.
     *
     * @param description the task description
     */
    public Todo(String description) {
        super(description);
    }

    @Override
    public String toString() {
        return "[T]" + super.toString();
    }
}
