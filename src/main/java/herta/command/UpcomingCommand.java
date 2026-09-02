package herta.command;

import java.time.DateTimeException;
import java.time.LocalDateTime;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.UiOutput;

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
     * @param ui the output interface used to display responses
     * @param storage unused because querying does not change stored data
     * @throws HertaException if the requested time range is too large
     */
    @Override
    public void execute(TaskList tasks, UiOutput ui, Storage storage) throws HertaException {
        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime until;
        try {
            until = now.plusDays(days);
        } catch (DateTimeException e) {
            throw new HertaException("You want me to look that far ahead? Use a smaller number of days.");
        }

        showMatchingTasks(tasks,
                task -> !task.isDone() && task.isUpcoming(now, until),
                "Your next " + days + " days. Try not to fall behind:",
                "Nothing upcoming. Enjoy the silence while it lasts.",
                ui);
    }
}
