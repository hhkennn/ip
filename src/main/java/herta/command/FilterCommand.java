package herta.command;

import herta.parser.DateTimeParser;
import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.Ui;
import java.time.LocalDate;

/**
 * Represents the command that displays deadlines and events occurring on a date.
 */
public class FilterCommand extends QueryCommand {
    private final LocalDate date;

    /**
     * Creates a command that filters tasks for a specific date.
     *
     * @param date the date on which matching tasks must occur
     */
    public FilterCommand(LocalDate date) {
        this.date = date;
    }

    /**
     * Displays tasks occurring on the command's date.
     *
     * @param tasks the task list to search
     * @param ui the user interface used for output
     * @param storage unused because filtering does not change stored data
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) {
        String displayDate = DateTimeParser.formatDateForDisplay(date);
        showMatchingTasks(tasks, task -> task.occursOn(date),
                "Tasks occurring on " + displayDate + ":",
                "No deadlines or events occur on " + displayDate + ".", ui);
    }
}
