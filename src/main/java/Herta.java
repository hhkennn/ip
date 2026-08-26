import java.util.Optional;

/**
 * Provides the command-line entry point for the Herta task manager.
 */
public class Herta {
    /**
     * Starts Herta and processes commands entered by the user.
     *
     * @param args command-line arguments, which are not used.
     */
    public static void main(String[] args) {
        Ui ui = new Ui();
        Storage storage = new Storage("data/herta.txt");
        Parser parser = new Parser();
        ui.showWelcome();

        TaskList tasks;
        try {
            tasks = storage.load();
        } catch (HertaException e) {
            ui.showMessage(e.getMessage());
            ui.close();
            return;
        }

        while (true) {
            String input = ui.readCommand();
            if (input == null) {
                ui.showSeparator();
                ui.showGoodbye();
                ui.close();
                break;
            }
            ui.showSeparator();

            try {
                Optional<Command> parsedCommand = parser.parse(input);
                if (parsedCommand.isPresent()) {
                    Command command = parsedCommand.get();
                    command.execute(tasks, ui, storage);
                    if (command.isExit()) {
                        ui.close();
                        break;
                    }
                } else {
                    CommandType commandType = parser.parseCommandType(input);

                    switch (commandType) {
                        case UNKNOWN:
                            if (input.isEmpty()) {
                                throw new HertaException("Nothing? Were you expecting me to read your mind?");
                            }
                            throw new HertaException("That command is invalid. Were you just guessing?\n" +
                                    "Try todo, deadline, event, list, filter, upcoming, sort, "
                                    + "mark, unmark, delete, and bye.");
                        default:
                            break;
                    }
                }
            } catch (HertaException e) {
                ui.showMessage(e.getMessage());
            }

            ui.showSeparator();
        }
    }

}
