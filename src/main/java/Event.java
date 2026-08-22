/**
 * Represents a task that starts and ends at specified dates or times.
 */
public class Event extends Task {
    protected String from;
    protected String to;

    /**
     * Creates an incomplete event task.
     *
     * @param description the task description.
     * @param from the event's start date or time.
     * @param to the event's end date or time.
     */
    public Event(String description, String from, String to) {
        super(description);
        this.from = from;
        this.to = to;
    }

    /**
     * Returns this event formatted with its task type, completion status, description,
     * and time range.
     *
     * @return the formatted event text.
     */
    @Override
    public String toString() {
        return "[E]" + super.toString() + " (from: " + from + " to: " + to + ")";
    }
}
