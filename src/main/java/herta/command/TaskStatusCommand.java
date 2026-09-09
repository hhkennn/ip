package herta.command;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.Task;
import herta.task.TaskList;

/**
 * Base class for commands that change a selected task's completion status.
 */
public abstract class TaskStatusCommand extends TaskIndexCommand {

    /**
     * Creates a command for a zero-based task index.
     *
     * @param taskIndex the zero-based index of the selected task
     */
    protected TaskStatusCommand(int taskIndex) {
        super(taskIndex);
    }

    /**
     * Updates and persists the selected task's completion status.
     *
     * @param tasks the task list to update
     * @param storage the storage used to persist the status change
     * @param shouldBeDone the completion status to apply
     * @return the updated task
     * @throws HertaException if the selected task is invalid or cannot be saved
     */
    protected Task updateTaskStatus(TaskList tasks, Storage storage, boolean shouldBeDone)
            throws HertaException {
        int taskIndex = getTaskIndex(tasks);
        boolean wasDone = shouldBeDone
                ? tasks.markTask(taskIndex)
                : tasks.unmarkTask(taskIndex);
        Task task = tasks.get(taskIndex);
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            tasks.restoreStatus(taskIndex, wasDone);
            throw e;
        }
        return task;
    }
}
