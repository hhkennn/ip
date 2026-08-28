package herta.command;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.TaskList;
import herta.ui.Ui;

/**
 * Represents an empty or unrecognised user command.
 */
public class UnknownCommand extends Command {
    private final boolean emptyInput;

    /**
     * Creates a command that reports the appropriate error for the input.
     *
     * @param input the complete user input
     */
    public UnknownCommand(String input) {
        emptyInput = input.isEmpty();
    }

    /**
     * Rejects the input with the same explanation used by the original dispatcher.
     *
     * @param tasks unused because the input is not a supported task command
     * @param ui unused because errors are propagated to the main loop
     * @param storage unused because the input does not change stored data
     * @throws HertaException describing why the input was rejected
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) throws HertaException {
        if (emptyInput) {
            throw new HertaException("Nothing? Were you expecting me to read your mind?");
        }
        throw new HertaException("That command is invalid. Were you just guessing?\n"
                + "Try todo, deadline, event, list, find, filter, upcoming, sort, "
                + "mark, unmark, delete, and bye.");
    }
}
