package herta.command;

import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.Ui;

/**
 * Represents the command that ends the Herta application.
 */
public class ExitCommand extends Command {

    /**
     * Displays the goodbye message.
     *
     * @param tasks unused because exiting does not access tasks
     * @param ui the user interface used to display the goodbye message
     * @param storage unused because exiting does not access storage
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) {
        ui.showGoodbye();
    }

    /**
     * Marks this command as terminating the application.
     *
     * @return always {@code true}
     */
    @Override
    public boolean isExit() {
        return true;
    }
}
