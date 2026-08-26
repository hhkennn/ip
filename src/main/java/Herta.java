/**
 * Provides the command-line entry point for the Herta task manager.
 */
public class Herta {
    private final Ui ui;
    private final Storage storage;
    private final Parser parser;
    private final TaskList tasks;
    private final String loadingError;

    /**
     * Creates a Herta instance backed by the specified data file.
     *
     * @param filePath the path of Herta's data file
     */
    public Herta(String filePath) {
        ui = new Ui();
        storage = new Storage(filePath);
        parser = new Parser();

        TaskList loadedTasks;
        String loadError = null;
        try {
            loadedTasks = storage.load();
        } catch (HertaException e) {
            loadedTasks = new TaskList();
            loadError = e.getMessage();
        }
        tasks = loadedTasks;
        loadingError = loadError;
    }

    /**
     * Starts Herta and processes commands entered by the user.
     */
    public void run() {
        ui.showWelcome();

        if (loadingError != null) {
            ui.showMessage(loadingError);
            ui.close();
            return;
        }

        boolean isExit = false;
        while (!isExit) {
            String input = ui.readCommand();
            if (input == null) {
                ui.showSeparator();
                ui.showGoodbye();
                break;
            }
            ui.showSeparator();

            try {
                Command command = parser.parse(input);
                command.execute(tasks, ui, storage);
                isExit = command.isExit();
            } catch (HertaException e) {
                ui.showMessage(e.getMessage());
            }

            if (!isExit) {
                ui.showSeparator();
            }
        }

        ui.close();
    }

    /**
     * Launches Herta using its default data file.
     *
     * @param args command-line arguments, which are not used
     */
    public static void main(String[] args) {
        new Herta("data/herta.txt").run();
    }
}
