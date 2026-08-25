import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a task that must be completed before a given date or time.
 */
public class Deadline extends Task {
    protected final LocalDateTime by;

    /**
     * Creates an incomplete deadline task.
     *
     * @param description the task description.
     * @param by the date and time by which the task should be completed.
     */
    public Deadline(String description, LocalDateTime by) {
        super(description);
        this.by = Objects.requireNonNull(by, "Deadline date/time cannot be null.");
    }

    /**
     * Returns the deadline date and time.
     *
     * @return the deadline date and time
     */
    public LocalDateTime getBy() {
        return by;
    }

    /**
     * Returns this deadline in the format used by Herta's data file.
     *
     * @return the serialized deadline text.
     */
    @Override
    public String toStorageString() {
        return "D | " + getCompletionStatusCode() + " | " + description + " | "
                + DateTimeParser.formatForStorage(by);
    }

    /**
     * Returns this deadline formatted with its task type, completion status, description,
     * and due date.
     *
     * @return the formatted deadline text.
     */
    @Override
    public String toString() {
        return "[D]" + super.toString() + " (by: "
                + DateTimeParser.formatForDisplay(by) + ")";
    }
}
