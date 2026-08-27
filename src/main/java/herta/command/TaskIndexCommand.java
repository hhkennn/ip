package herta.command;

import herta.exception.HertaException;
import herta.task.TaskList;

/**
 * Base class for commands that operate on one task selected by its list number.
 */
public abstract class TaskIndexCommand extends Command {
    private final int taskIndex;

    /**
     * Creates a command for a zero-based task index.
     *
     * @param taskIndex the zero-based index of the selected task
     */
    protected TaskIndexCommand(int taskIndex) {
        this.taskIndex = taskIndex;
    }

    /**
     * Validates the selected index against the current task list.
     *
     * @param tasks the task list containing the selected task
     * @return the validated zero-based task index
     * @throws HertaException if the selected task does not exist
     */
    protected int getTaskIndex(TaskList tasks) throws HertaException {
        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            throw new HertaException("That task doesn't exist. Did you even check the list?");
        }
        return taskIndex;
    }
}
