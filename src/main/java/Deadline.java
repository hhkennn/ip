import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

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
     * Reconstructs a deadline from its serialized date/time value.
     *
     * @param description the deadline description
     * @param byText the serialized deadline date/time
     * @return the reconstructed deadline
     * @throws IllegalArgumentException if the stored date/time is invalid
     */
    public static Deadline fromStorage(String description, String byText) {
        try {
            return new Deadline(description, DateTimeParser.parseStoredValue(byText));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid saved deadline date/time: "
                    + byText + ".", e);
        }
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
     * Checks whether this deadline falls on a particular calendar date.
     *
     * @param date the date to check
     * @return {@code true} if the deadline is due on the date
     */
    @Override
    public boolean occursOn(LocalDate date) {
        return by.toLocalDate().equals(date);
    }

    /**
     * Returns the deadline time used for chronological operations.
     *
     * @return the deadline's due date and time
     */
    @Override
    public Optional<LocalDateTime> getScheduledDateTime() {
        return Optional.of(by);
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
