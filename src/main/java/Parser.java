import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Interprets user commands and converts their arguments into domain values.
 */
public class Parser {

    /**
     * Identifies the command represented by the user's input.
     *
     * @param input the complete user input
     * @return the corresponding command type, or {@link CommandType#UNKNOWN}
     */
    public CommandType parseCommandType(String input) {
        return CommandType.fromInput(input);
    }

    /**
     * Parses commands that have been migrated to command objects.
     *
     * <p>The optional is empty for commands that are still handled by Herta's
     * temporary legacy dispatch. This allows the command hierarchy to be
     * introduced incrementally without changing the remaining commands yet.</p>
     *
     * @param input the complete user input
     * @return the parsed command when this command has been migrated
     * @throws HertaException if command-specific parsing fails
     */
    public Optional<Command> parse(String input) throws HertaException {
        switch (parseCommandType(input)) {
            case BYE:
                return Optional.of(new ExitCommand());
            case LIST:
                return Optional.of(new ListCommand());
            case TODO:
                return Optional.of(new TodoCommand(parseTodo(input)));
            case DEADLINE:
                return Optional.of(new DeadlineCommand(parseDeadline(input)));
            case EVENT:
                return Optional.of(new EventCommand(parseEvent(input)));
            case DELETE:
                return Optional.of(new DeleteCommand(parseTaskIndex(input, "delete")));
            default:
                return Optional.empty();
        }
    }

    /**
     * Parses a todo command into a todo task.
     *
     * @param input the complete todo command
     * @return the parsed todo task
     * @throws HertaException if the todo description is empty
     */
    public Todo parseTodo(String input) throws HertaException {
        String description = input.substring("todo".length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("A blank todo? Even I can't organise nothing. "
                    + "Use: todo <description>.");
        }
        return new Todo(description);
    }

    /**
     * Parses a deadline command into a deadline task.
     *
     * @param input the complete deadline command
     * @return the parsed deadline task
     * @throws HertaException if the command format or date/time is invalid
     */
    public Deadline parseDeadline(String input) throws HertaException {
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

        return new Deadline(description, parseUserDateTime(byInput,
                "Invalid deadline date/time. Use a date such as "
                        + "2019-10-15 or a date/time such as 2/12/2019 1800."));
    }

    /**
     * Parses an event command into an event task.
     *
     * @param input the complete event command
     * @return the parsed event task
     * @throws HertaException if the command format, date/time, or event range is invalid
     */
    public Event parseEvent(String input) throws HertaException {
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

        LocalDateTime from = parseUserDateTime(fromInput,
                "Invalid event date/time. Use a date such as "
                        + "2019-10-15 or a date/time such as 2/12/2019 1800.");
        LocalDateTime to = parseUserDateTime(toInput,
                "Invalid event date/time. Use a date such as "
                        + "2019-10-15 or a date/time such as 2/12/2019 1800.");
        try {
            return new Event(description, from, to);
        } catch (IllegalArgumentException e) {
            throw new HertaException("An event must end after it starts.");
        }
    }

    /**
     * Parses the date from a filter command.
     *
     * @param input the complete filter command
     * @return the requested filter date
     * @throws HertaException if the command format or date is invalid
     */
    public LocalDate parseFilterDate(String input) throws HertaException {
        String[] parts = input.substring("filter".length()).trim().split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("/on")) {
            throw new HertaException("Use: filter /on <date>.");
        }

        try {
            return DateTimeParser.parseUserDate(parts[1]);
        } catch (DateTimeParseException e) {
            throw new HertaException("Invalid filter date. Use a date such as "
                    + "2019-10-15 or 15/10/2019.");
        }
    }

    /**
     * Parses the number of days from an upcoming command.
     *
     * @param input the complete upcoming command
     * @return a positive number of days
     * @throws HertaException if the command does not contain a positive number
     */
    public int parseUpcomingDays(String input) throws HertaException {
        String daysInput = input.substring("upcoming".length()).trim();
        try {
            int days = Integer.parseInt(daysInput);
            if (days <= 0) {
                throw new NumberFormatException();
            }
            return days;
        } catch (NumberFormatException e) {
            throw new HertaException("Use: upcoming <positive number of days>.");
        }
    }

    /**
     * Validates a sort command.
     *
     * @param input the complete sort command
     * @throws HertaException if the command does not request date sorting
     */
    public void validateSortCommand(String input) throws HertaException {
        if (!input.equals("sort date")) {
            throw new HertaException("Use: sort date.");
        }
    }

    /**
     * Parses a one-based task number from a task-selection command.
     *
     * @param input the complete task-selection command
     * @param command the command keyword used in the input
     * @return the corresponding zero-based task index
     * @throws HertaException if the task number is not numeric
     */
    public int parseTaskIndex(String input, String command) throws HertaException {
        String taskNumber = input.substring(command.length()).trim();
        try {
            return Integer.parseInt(taskNumber) - 1;
        } catch (NumberFormatException e) {
            throw new HertaException("That's not a task number. Try: " + command + " 1.");
        }
    }

    /**
     * Parses a user-provided date/time and converts parsing failures into a
     * command-specific explanation.
     *
     * @param input the date/time text
     * @param errorMessage the explanation to use when parsing fails
     * @return the parsed date/time
     * @throws HertaException if the date/time is invalid
     */
    private LocalDateTime parseUserDateTime(String input, String errorMessage)
            throws HertaException {
        try {
            return DateTimeParser.parseUserInput(input);
        } catch (DateTimeParseException e) {
            throw new HertaException(errorMessage);
        }
    }
}
