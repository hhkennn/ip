/**
 * Base class for commands that append a task to the task list.
 */
public abstract class AddCommand extends Command {
    private final Task task;

    /**
     * Creates an add command for a particular task.
     *
     * @param task the task to append when the command executes
     */
    protected AddCommand(Task task) {
        this.task = task;
    }

    /**
     * Saves the updated list, then updates the live list and confirms the addition.
     *
     * @param tasks the task list to update
     * @param ui the user interface used for output
     * @param storage the storage used to persist the updated list
     * @throws HertaException if the updated list cannot be saved
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) throws HertaException {
        TaskList updatedTasks = new TaskList(tasks.asUnmodifiableList());
        updatedTasks.add(task);
        storage.save(updatedTasks);
        tasks.add(task);
        ui.showMessage("There. I've added it:");
        ui.showTask(task);
        ui.showTaskCount(tasks.size());
    }
}
