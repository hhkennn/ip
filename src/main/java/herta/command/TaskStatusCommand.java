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
     * @param shouldBeCompleted the completion status to apply
     * @return the updated task
     * @throws HertaException if the selected task is invalid or cannot be saved
     */
    protected Task updateTaskStatus(TaskList tasks, Storage storage, boolean shouldBeCompleted)
            throws HertaException {
        int taskIndex = getTaskIndex(tasks);
        boolean wasCompleted = tasks.get(taskIndex).isCompleted();
        if (shouldBeCompleted) {
            tasks.markTask(taskIndex);
        } else {
            tasks.unmarkTask(taskIndex);
        }
        Task task = tasks.get(taskIndex);
        assert task.isCompleted() == shouldBeCompleted
                : "The task status must match the requested status before saving.";
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            tasks.restoreStatus(taskIndex, wasCompleted);
            assert tasks.get(taskIndex).isCompleted() == wasCompleted
                    : "A failed status update must restore the previous status.";
            throw e;
        }
        return task;
    }
}
