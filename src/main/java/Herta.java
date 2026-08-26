import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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
                        case FILTER:
                            filterTasks(tasks, input, ui, parser);
                            break;
                        case UPCOMING:
                            printUpcomingTasks(tasks, input, ui, parser);
                            break;
                        case SORT:
                            sortTasks(tasks, input, ui, parser);
                            break;
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

    /**
     * Prints deadlines and events occurring on a requested date.
     *
     * @param tasks the task list to search
     * @param input the complete filter command entered by the user
     * @param ui the user interface used for output
     * @param parser the parser used to interpret the filter command
     * @throws HertaException if the command or date is invalid
     */
    private static void filterTasks(TaskList tasks, String input, Ui ui, Parser parser)
            throws HertaException {
        LocalDate date = parser.parseFilterDate(input);

        String displayDate = DateTimeParser.formatDateForDisplay(date);
        printMatchingTasks(tasks, task -> task.occursOn(date),
                "Tasks occurring on " + displayDate + ":",
                "No deadlines or events occur on " + displayDate + ".", ui);
    }

    /**
     * Prints incomplete deadlines and events beginning within a future window.
     *
     * @param tasks the task list to search
     * @param input the complete upcoming command entered by the user
     * @param ui the user interface used for output
     * @param parser the parser used to interpret the upcoming command
     * @throws HertaException if the command or number of days is invalid
     */
    private static void printUpcomingTasks(TaskList tasks, String input, Ui ui, Parser parser)
            throws HertaException {
        int days = parser.parseUpcomingDays(input);

        LocalDateTime now = LocalDateTime.now();
        final LocalDateTime until;
        try {
            until = now.plusDays(days);
        } catch (DateTimeException e) {
            throw new HertaException("The upcoming time range is too large.");
        }

        printMatchingTasks(tasks,
                task -> !task.isDone() && task.isUpcoming(now, until),
                "Upcoming deadlines and events in the next " + days + " days:",
                "No incomplete deadlines or events are upcoming in the next " + days + " days.",
                ui);
    }

    /**
     * Prints all tasks in chronological order without changing their stored order.
     * Tasks without dates are placed at the end, and displayed numbers remain
     * their original task-list numbers.
     *
     * @param tasks the task list to sort for display
     * @param input the complete sort command entered by the user
     * @param ui the user interface used for output
     * @param parser the parser used to interpret the sort command
     * @throws HertaException if the requested sort is invalid
     */
    private static void sortTasks(TaskList tasks, String input, Ui ui, Parser parser)
            throws HertaException {
        parser.validateSortCommand(input);

        List<Integer> sortedIndices = tasks.sortedIndices(Comparator.comparing(
                (Task task) -> task.getScheduledDateTime().orElse(LocalDateTime.MAX)));

        ui.showMessage("Here are your tasks sorted by date:");
        for (int index : sortedIndices) {
            ui.showMessage((index + 1) + "." + tasks.get(index));
        }
    }

    /**
     * Prints tasks selected by a date/time-related query.
     *
     * @param tasks the task list to search
     * @param matcher the condition a task must satisfy
     * @param heading the heading to print before the results
     * @param emptyMessage the message to print when there are no matches
     * @param ui the user interface used for output
     */
    private static void printMatchingTasks(TaskList tasks, Predicate<Task> matcher,
                                           String heading, String emptyMessage, Ui ui) {
        ui.showMessage(heading);
        List<Integer> matchingIndices = tasks.matchingIndices(matcher);
        for (int index : matchingIndices) {
            ui.showMessage((index + 1) + "." + tasks.get(index));
        }
        if (matchingIndices.isEmpty()) {
            ui.showMessage(emptyMessage);
        }
    }

}
