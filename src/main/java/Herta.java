import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        ui.showWelcome();

        List<Task> tasks;
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
                CommandType commandType = CommandType.fromInput(input);

                if (commandType == CommandType.BYE) {
                    ui.showGoodbye();
                    ui.close();
                    break;
                }

                switch (commandType) {
                    case LIST:
                        printTaskList(tasks, ui);
                        break;
                    case FILTER:
                        filterTasks(tasks, input, ui);
                        break;
                    case UPCOMING:
                        printUpcomingTasks(tasks, input, ui);
                        break;
                    case SORT:
                        sortTasks(tasks, input, ui);
                        break;
                    case MARK:
                        markTask(tasks, input, ui, storage);
                        break;
                    case UNMARK:
                        unmarkTask(tasks, input, ui, storage);
                        break;
                    case DELETE:
                        deleteTask(tasks, input, ui, storage);
                        break;
                    case TODO:
                        addTodo(tasks, input, ui, storage);
                        break;
                    case DEADLINE:
                        addDeadline(tasks, input, ui, storage);
                        break;
                    case EVENT:
                        addEvent(tasks, input, ui, storage);
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
            } catch (HertaException e) {
                ui.showMessage(e.getMessage());
            }

            ui.showSeparator();
        }
    }

    /**
     * Adds a task and prints the standard confirmation message.
     *
     * @param tasks the task list to update.
     * @param task the task to add.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the updated list
     */
    private static void addTask(List<Task> tasks, Task task, Ui ui, Storage storage)
            throws HertaException {
        List<Task> updatedTasks = new ArrayList<>(tasks);
        updatedTasks.add(task);
        storage.save(updatedTasks);
        tasks.add(task);
        ui.showMessage("There. I've added it:");
        ui.showTask(task);
        ui.showTaskCount(tasks.size());
    }

    /**
     * Prints every task in its current order.
     *
     * @param tasks the task list to print
     * @param ui the user interface used for output
     */
    private static void printTaskList(List<Task> tasks, Ui ui) {
        ui.showMessage("Let's see what you've managed to pile up:");
        for (int i = 0; i < tasks.size(); i++) {
            ui.showMessage((i + 1) + "." + tasks.get(i));
        }
    }

    /**
     * Prints deadlines and events occurring on a requested date.
     *
     * @param tasks the task list to search
     * @param input the complete filter command entered by the user
     * @param ui the user interface used for output
     * @throws HertaException if the command or date is invalid
     */
    private static void filterTasks(List<Task> tasks, String input, Ui ui) throws HertaException {
        String[] parts = input.substring("filter".length()).trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("/on")) {
            throw new HertaException("Use: filter /on <date>.");
        }

        final LocalDate date;
        try {
            date = DateTimeParser.parseUserDate(parts[1]);
        } catch (DateTimeParseException e) {
            throw new HertaException("Invalid filter date. Use a date such as "
                    + "2019-10-15 or 15/10/2019.");
        }

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
     * @throws HertaException if the command or number of days is invalid
     */
    private static void printUpcomingTasks(List<Task> tasks, String input, Ui ui)
            throws HertaException {
        String daysInput = input.substring("upcoming".length()).trim();
        final int days;
        try {
            days = Integer.parseInt(daysInput);
            if (days <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new HertaException("Use: upcoming <positive number of days>.");
        }

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
     * @throws HertaException if the requested sort is invalid
     */
    private static void sortTasks(List<Task> tasks, String input, Ui ui) throws HertaException {
        if (!input.equals("sort date")) {
            throw new HertaException("Use: sort date.");
        }

        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            sortedIndices.add(i);
        }
        sortedIndices.sort(Comparator.comparing(
                (Integer index) -> tasks.get(index).getScheduledDateTime()
                        .orElse(LocalDateTime.MAX)));

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
    private static void printMatchingTasks(List<Task> tasks, Predicate<Task> matcher,
                                           String heading, String emptyMessage, Ui ui) {
        ui.showMessage(heading);
        boolean hasMatches = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (matcher.test(tasks.get(i))) {
                ui.showMessage((i + 1) + "." + tasks.get(i));
                hasMatches = true;
            }
        }
        if (!hasMatches) {
            ui.showMessage(emptyMessage);
        }
    }

    /**
     * Parses and adds a todo command's description.
     *
     * @param tasks the task list to update.
     * @param input the complete todo command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the new task
     * @throws HertaException if the todo description is empty
     */
    private static void addTodo(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String description = input.substring("todo".length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("A blank todo? Even I can't organise nothing. Use: todo <description>.");
        }
        addTask(tasks, new Todo(description), ui, storage);
    }

    /**
     * Parses and adds a deadline command's description and date/time.
     *
     * @param tasks the task list to update.
     * @param input the complete deadline command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the new task
     * @throws HertaException if the deadline format or required fields are invalid
     */
    private static void addDeadline(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String content = input.substring("deadline".length()).trim();
        String[] deadlineParts = content.split("\\s+/by\\s+", 2);
        if (deadlineParts.length != 2) {
            throw new HertaException("Did you even read the deadline format? "
                    + "Use: deadline <description> /by <date/time>.");
        }

        String description = deadlineParts[0].trim();
        String byInput = deadlineParts[1].trim();
        if (description.isEmpty() || byInput.isEmpty()) {
            throw new HertaException("Did you even read the deadline format? "
                    + "Use: deadline <description> /by <date/time>.");
        }

        addTask(tasks, new Deadline(description, parseUserDeadline(byInput)), ui, storage);
    }

    /**
     * Parses a deadline entered in a user command.
     *
     * @param input the text after {@code /by}
     * @return the parsed deadline date/time
     * @throws HertaException if the input is not a supported date/time
     */
    private static LocalDateTime parseUserDeadline(String input) throws HertaException {
        try {
            return DateTimeParser.parseUserInput(input);
        } catch (DateTimeParseException e) {
            throw new HertaException("Invalid deadline date/time. Use a date such as "
                    + "2019-10-15 or a date/time such as 2/12/2019 1800.");
        }
    }

    /**
     * Parses and adds an event command's description, start, and end date/time.
     *
     * @param tasks the task list to update.
     * @param input the complete event command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the new task
     * @throws HertaException if the event format or required fields are invalid
     */
    private static void addEvent(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String content = input.substring("event".length()).trim();
        String[] eventParts = content.split("\\s+/from\\s+", 2);
        if (eventParts.length != 2) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        String[] timeParts = eventParts[1].split("\\s+/to\\s+", 2);
        if (timeParts.length != 2) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        String description = eventParts[0].trim();
        String fromInput = timeParts[0].trim();
        String toInput = timeParts[1].trim();
        if (description.isEmpty() || fromInput.isEmpty() || toInput.isEmpty()) {
            throw new HertaException("Did you even read the event format? "
                    + "Use: event <description> /from <start> /to <end>.");
        }

        LocalDateTime from = parseUserEventDateTime(fromInput);
        LocalDateTime to = parseUserEventDateTime(toInput);
        try {
            addTask(tasks, new Event(description, from, to), ui, storage);
        } catch (IllegalArgumentException e) {
            throw new HertaException("An event must end after it starts.");
        }
    }

    /**
     * Parses an event date/time entered in a user command.
     *
     * @param input the event date/time text
     * @return the parsed event date/time
     * @throws HertaException if the input is not a supported date/time
     */
    private static LocalDateTime parseUserEventDateTime(String input) throws HertaException {
        try {
            return DateTimeParser.parseUserInput(input);
        } catch (DateTimeParseException e) {
            throw new HertaException("Invalid event date/time. Use a date such as "
                    + "2019-10-15 or a date/time such as 2/12/2019 1800.");
        }
    }

    /**
     * Deletes the requested task and prints the standard confirmation message.
     *
     * @param tasks the task list to update.
     * @param input the complete delete command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the deletion
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void deleteTask(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String taskNumber = input.substring("delete".length()).trim();
        Task task = getTask(tasks, taskNumber, "delete");
        List<Task> updatedTasks = new ArrayList<>(tasks);
        updatedTasks.remove(task);
        storage.save(updatedTasks);
        tasks.remove(task);
        ui.showMessage("There. It's gone:");
        ui.showTask(task);
        ui.showTaskCount(tasks.size());
    }

    /**
     * Marks the requested task as done.
     *
     * @param tasks the task list to update.
     * @param input the complete mark command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the status change
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void markTask(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String taskNumber = input.substring("mark".length()).trim();
        Task task = getTask(tasks, taskNumber, "mark");
        boolean wasDone = task.isDone();
        task.markAsDone();
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            restoreTaskStatus(task, wasDone);
            throw e;
        }
        ui.showMessage("There. It's marked complete:");
        ui.showTask(task);
    }

    /**
     * Marks the requested task as not done.
     *
     * @param tasks the task list to update.
     * @param input the complete unmark command entered by the user.
     * @param ui the user interface used for output
     * @param storage the storage used to persist the status change
     * @throws HertaException if the task number is missing, invalid, or out of range
     */
    private static void unmarkTask(List<Task> tasks, String input, Ui ui, Storage storage)
            throws HertaException {
        String taskNumber = input.substring("unmark".length()).trim();
        Task task = getTask(tasks, taskNumber, "unmark");
        boolean wasDone = task.isDone();
        task.markAsNotDone();
        try {
            storage.save(tasks);
        } catch (HertaException e) {
            restoreTaskStatus(task, wasDone);
            throw e;
        }
        ui.showMessage("As you wish. It's incomplete again:");
        ui.showTask(task);
    }

    /**
     * Restores a task's completion status after a failed save.
     *
     * @param task the task whose status should be restored.
     * @param wasDone the status before the attempted update.
     */
    private static void restoreTaskStatus(Task task, boolean wasDone) {
        if (wasDone) {
            task.markAsDone();
        } else {
            task.markAsNotDone();
        }
    }

    /**
     * Finds a task using the one-based number shown by the list command.
     *
     * @param tasks the task list to search.
     * @param taskNumber the task number entered by the user.
     * @param command the command used to request the task.
     * @return the requested task
     * @throws HertaException if the task number is invalid or out of range
     */
    private static Task getTask(List<Task> tasks, String taskNumber, String command) throws HertaException {
        final int taskIndex;
        try {
            taskIndex = Integer.parseInt(taskNumber) - 1;
        } catch (NumberFormatException e) {
            throw new HertaException("That's not a task number. Try: " + command + " 1.");
        }

        if (taskIndex < 0 || taskIndex >= tasks.size()) {
            throw new HertaException("That task doesn't exist. Did you even check the list?");
        }
        return tasks.get(taskIndex);
    }
}
