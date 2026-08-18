/**
 * Represents a task that must be completed before a given date or time.
 */
public class Deadline extends Task {
    protected String by;

    /**
     * Creates an incomplete deadline task.
     *
     * @param description the task description
     * @param by the date or time by which the task should be completed
     */
    public Deadline(String description, String by) {
        super(description);
        this.by = by;
    }

    @Override
    public String toString() {
        return "[D]" + super.toString() + " (by: " + by + ")";
    }
}
