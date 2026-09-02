package herta.command;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import herta.storage.Storage;
import herta.task.Task;
import herta.task.TaskList;
import herta.ui.UiOutput;

/**
 * Represents the command that displays tasks in chronological order.
 */
public class SortCommand extends Command {

    /**
     * Displays all tasks sorted by their scheduled date without changing the
     * order stored in the task list.
     *
     * @param tasks the task list to sort for display
     * @param ui the output interface used to display responses
     * @param storage unused because sorting does not change stored data
     */
    @Override
    public void execute(TaskList tasks, UiOutput ui, Storage storage) {
        List<Integer> sortedIndices = tasks.sortedIndices(Comparator.comparing((Task task) ->
                task.getScheduledDateTime().orElse(LocalDateTime.MAX)));

        ui.showMessage("Here are your tasks sorted by date:");
        for (int index : sortedIndices) {
            ui.showMessage((index + 1) + "." + tasks.get(index));
        }
    }
}
