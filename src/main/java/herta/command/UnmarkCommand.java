package herta.command;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.Task;
import herta.task.TaskList;
import herta.ui.UiOutput;

/**
 * Represents the command that marks a task as incomplete.
 */
public class UnmarkCommand extends TaskIndexCommand {

    /**
     * Creates a command that marks the task at the given index as incomplete.
     *
     * @param taskIndex the zero-based index of the task to unmark
     */
    public UnmarkCommand(int taskIndex) {
        super(taskIndex);
    }

    /**
     * Unmarks the selected task, persists the updated status, and reports it.
     * The in-memory status is restored if saving fails.
     *
     * @param tasks the task list to update
     * @param ui the output interface used to display responses
     * @param storage the storage used to persist the status change
     * @throws HertaException if the selected task is invalid or cannot be saved
     */
    @Override
    public void execute(TaskList tasks, UiOutput ui, Storage storage) throws HertaException {
        int taskIndex = getTaskIndex(tasks);
        boolean wasDone = tasks.unmarkTask(taskIndex);
        Task task = tasks.get(taskIndex);
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            tasks.restoreStatus(taskIndex, wasDone);
            throw e;
        }
        ui.showMessage("As you wish. It's incomplete again:");
        ui.showTask(task);
    }
}
