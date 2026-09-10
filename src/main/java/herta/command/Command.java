package herta.command;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.UiOutput;

/**
 * Represents an executable command entered by the user.
 */
public abstract class Command {

    /**
     * Executes this command using the application's shared collaborators.
     *
     * @param tasks the task list to read or update
     * @param ui the output interface used to display responses
     * @param storage the storage used to persist changes
     * @throws HertaException if the command cannot be completed
     */
    public abstract void execute(TaskList tasks, UiOutput ui, Storage storage)
            throws HertaException;

}
