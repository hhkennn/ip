package herta.parser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import herta.command.Command;
import herta.command.DeadlineCommand;
import herta.command.DeleteCommand;
import herta.command.EventCommand;
import herta.command.ExitCommand;
import herta.command.FilterCommand;
import herta.command.FindCommand;
import herta.command.ListCommand;
import herta.command.MarkCommand;
import herta.command.SortCommand;
import herta.command.TodoCommand;
import herta.command.UnknownCommand;
import herta.command.UnmarkCommand;
import herta.command.UpcomingCommand;
import herta.exception.HertaException;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.Todo;

/**
 * Interprets user commands and converts their arguments into domain values.
 */
public class Parser {
    private static final String EVENT_FORMAT_ERROR = "Did you even read the event format? "
            + "Use: event <description> /from <start> /to <end>.";
    private static final String EVENT_DATE_ERROR = "Those dates won't do. Use something valid, "
            + "such as 2019-10-15 or 2/12/2019 1800.";
    private static final String UPCOMING_RANGE_ERROR = "That range makes no sense. "
            + "Use a positive number of days.";

    /** Holds the validated fields extracted from an event command. */
    private record EventParts(String description, String fromInput, String toInput) {
    }

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
     * Parses user input into an executable command.
     *
     * @param input the complete user input
     * @return a command representing the input, including an
     *         {@link UnknownCommand} for unsupported input
     * @throws HertaException if command-specific parsing fails
     */
    public Command parse(String input) throws HertaException {
        return parse(input, parseCommandType(input));
    }

    /**
     * Parses user input using a command type that has already been identified.
     *
     * @param input the complete user input
     * @param commandType the command type identified for the input
     * @return a command representing the input, including an
     *         {@link UnknownCommand} for unsupported input
     * @throws HertaException if command-specific parsing fails
     */
    public Command parse(String input, CommandType commandType) throws HertaException {
        return switch (commandType) {
            case BYE -> new ExitCommand();
            case LIST -> new ListCommand();
            case FIND -> new FindCommand(parseFindKeyword(input));
            case TODO -> new TodoCommand(parseTodo(input));
            case DEADLINE -> new DeadlineCommand(parseDeadline(input));
            case EVENT -> new EventCommand(parseEvent(input));
            case DELETE -> new DeleteCommand(parseTaskIndex(input, CommandType.DELETE.getKeyword()));
            case MARK -> new MarkCommand(parseTaskIndex(input, CommandType.MARK.getKeyword()));
            case UNMARK -> new UnmarkCommand(parseTaskIndex(input, CommandType.UNMARK.getKeyword()));
            case FILTER -> new FilterCommand(parseFilterDate(input));
            case UPCOMING -> new UpcomingCommand(parseUpcomingDays(input));
            case SORT -> {
                validateSortCommand(input);
                yield new SortCommand();
            }
            default -> new UnknownCommand(input);
        };
    }

    /**
     * Parses a todo command into a todo task.
     *
     * @param input the complete todo command
     * @return the parsed todo task
     * @throws HertaException if the todo description is empty
     */
    public Todo parseTodo(String input) throws HertaException {
        String description = getCommandArguments(input, CommandType.TODO);
        if (description.isEmpty()) {
            throw new HertaException("A blank todo? Even I can't organise nothing. "
                    + "Use: todo <description>.");
        }
        return new Todo(description);
    }

    /**
     * Parses the keyword from a find command.
     *
     * @param input the complete find command
     * @return the keyword to search for
     * @throws HertaException if the keyword is empty
     */
    public String parseFindKeyword(String input) throws HertaException {
        String keyword = getCommandArguments(input, CommandType.FIND);
        if (keyword.isEmpty()) {
            throw new HertaException("A blank search? Use: find <keyword>.");
        }
        return keyword;
    }

    /**
     * Parses a deadline command into a deadline task.
     *
     * @param input the complete deadline command
     * @return the parsed deadline task
     * @throws HertaException if the command format or date/time is invalid
     */
    public Deadline parseDeadline(String input) throws HertaException {
        String content = getCommandArguments(input, CommandType.DEADLINE);
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
                "That is not a date. Use a real one, such as 2019-10-15 or 2/12/2019 1800."));
    }

    /**
     * Parses an event command into an event task.
     *
     * @param input the complete event command
     * @return the parsed event task
     * @throws HertaException if the command format, date/time, or event range is invalid
     */
    public Event parseEvent(String input) throws HertaException {
        EventParts eventParts = parseEventParts(input);
        LocalDateTime from = parseUserDateTime(eventParts.fromInput(), EVENT_DATE_ERROR);
        LocalDateTime to = parseUserDateTime(eventParts.toInput(), EVENT_DATE_ERROR);
        return createEvent(eventParts.description(), from, to);
    }

    /**
     * Extracts and validates the description and date/time inputs from an event command.
     *
     * @param input the complete event command
     * @return the validated event fields
     * @throws HertaException if the command format or any field is invalid
     */
    private EventParts parseEventParts(String input) throws HertaException {
        String content = getCommandArguments(input, CommandType.EVENT);
        String[] eventParts = content.split("\\s+/from\\s+", 2);
        if (eventParts.length != 2) {
            throw new HertaException(EVENT_FORMAT_ERROR);
        }

        String[] timeParts = eventParts[1].split("\\s+/to\\s+", 2);
        if (timeParts.length != 2) {
            throw new HertaException(EVENT_FORMAT_ERROR);
        }

        String description = eventParts[0].trim();
        String fromInput = timeParts[0].trim();
        String toInput = timeParts[1].trim();
        if (description.isEmpty() || fromInput.isEmpty() || toInput.isEmpty()) {
            throw new HertaException(EVENT_FORMAT_ERROR);
        }
        return new EventParts(description, fromInput, toInput);
    }

    /**
     * Creates an event and translates invalid time ranges into a user-facing error.
     *
     * @param description the event description
     * @param from the event start
     * @param to the event end
     * @return the event with the supplied details
     * @throws HertaException if the end does not occur after the start
     */
    private Event createEvent(String description, LocalDateTime from, LocalDateTime to)
            throws HertaException {
        try {
            return new Event(description, from, to);
        } catch (IllegalArgumentException e) {
            throw new HertaException("Time moves forward. Make the event end after it starts.");
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
        String[] parts = getCommandArguments(input, CommandType.FILTER).split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals("/on")) {
            throw new HertaException("You forgot the /on. Use: filter /on <date>.");
        }

        try {
            return DateTimeParser.parseUserDate(parts[1]);
        } catch (DateTimeParseException e) {
            throw new HertaException("That date won't do. Try 2019-10-15 or 15/10/2019.");
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
        String daysInput = getCommandArguments(input, CommandType.UPCOMING);
        final int days;
        try {
            days = Integer.parseInt(daysInput);
        } catch (NumberFormatException e) {
            throw new HertaException(UPCOMING_RANGE_ERROR);
        }
        if (days <= 0) {
            throw new HertaException(UPCOMING_RANGE_ERROR);
        }
        return days;
    }

    /**
     * Validates a sort command.
     *
     * @param input the complete sort command
     * @throws HertaException if the command does not request date sorting
     */
    public void validateSortCommand(String input) throws HertaException {
        if (!input.equals("sort date")) {
            throw new HertaException("That is not a sorting option. Use: sort date.");
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
     * Returns the argument portion of a command after its recognized keyword.
     *
     * @param input the complete command input
     * @param commandType the command type whose keyword prefixes the input
     * @return the trimmed command arguments
     */
    private String getCommandArguments(String input, CommandType commandType) {
        return input.substring(commandType.getKeyword().length()).trim();
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
