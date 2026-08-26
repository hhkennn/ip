/**
 * Represents an executable command entered by the user.
 */
public abstract class Command {

    /**
     * Executes this command using the application's shared collaborators.
     *
     * @param tasks the task list to read or update
     * @param ui the user interface used for output
     * @param storage the storage used to persist changes
     * @throws HertaException if the command cannot be completed
     */
    public abstract void execute(TaskList tasks, Ui ui, Storage storage)
            throws HertaException;

    /**
     * Indicates whether executing this command should end the application.
     *
     * @return {@code true} when the command exits Herta
     */
    public boolean isExit() {
        return false;
    }
}
