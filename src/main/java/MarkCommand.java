/**
 * Represents the command that marks a task as complete.
 */
public class MarkCommand extends TaskIndexCommand {

    /**
     * Creates a command that marks the task at the given index.
     *
     * @param taskIndex the zero-based index of the task to mark
     */
    public MarkCommand(int taskIndex) {
        super(taskIndex);
    }

    /**
     * Marks the selected task, persists the updated status, and reports it.
     * The in-memory status is restored if saving fails.
     *
     * @param tasks the task list to update
     * @param ui the user interface used for output
     * @param storage the storage used to persist the status change
     * @throws HertaException if the selected task is invalid or cannot be saved
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) throws HertaException {
        int taskIndex = getTaskIndex(tasks);
        boolean wasDone = tasks.markTask(taskIndex);
        Task task = tasks.get(taskIndex);
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            tasks.restoreStatus(taskIndex, wasDone);
            throw e;
        }
        ui.showMessage("There. It's marked complete:");
        ui.showTask(task);
    }
}
