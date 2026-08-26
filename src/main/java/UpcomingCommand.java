import java.time.DateTimeException;
import java.time.LocalDateTime;

/**
 * Represents the command that displays incomplete tasks in a future time window.
 */
public class UpcomingCommand extends QueryCommand {
    private final int days;

    /**
     * Creates a command that searches the specified number of future days.
     *
     * @param days the positive number of days in the search window
     */
    public UpcomingCommand(int days) {
        this.days = days;
    }

    /**
     * Displays incomplete deadlines and events beginning in the future window.
     *
     * @param tasks the task list to search
     * @param ui the user interface used for output
     * @param storage unused because querying does not change stored data
     * @throws HertaException if the requested time range is too large
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) throws HertaException {
        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime until;
        try {
            until = now.plusDays(days);
        } catch (DateTimeException e) {
            throw new HertaException("The upcoming time range is too large.");
        }

        showMatchingTasks(tasks,
                task -> !task.isDone() && task.isUpcoming(now, until),
                "Upcoming deadlines and events in the next " + days + " days:",
                "No incomplete deadlines or events are upcoming in the next "
                        + days + " days.",
                ui);
    }
}
