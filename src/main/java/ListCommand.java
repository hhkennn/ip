/**
 * Represents the command that displays every task in the current order.
 */
public class ListCommand extends Command {

    /**
     * Displays the tasks managed by Herta.
     *
     * @param tasks the task list to display
     * @param ui the user interface used for output
     * @param storage unused because listing does not change saved data
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) {
        ui.showMessage("Let's see what you've managed to pile up:");
        for (int i = 0; i < tasks.size(); i++) {
            ui.showMessage((i + 1) + "." + tasks.get(i));
        }
    }
}
