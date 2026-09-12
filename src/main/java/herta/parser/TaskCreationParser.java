package herta.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import herta.exception.HertaException;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.Todo;

/**
 * Parses commands that create todo, deadline, and event tasks.
 */
final class TaskCreationParser {
    private static final String DEADLINE_MARKER = "/by";
    private static final String DEADLINE_FORMAT_ERROR = "Did you even read the deadline format? "
            + "Use: deadline <description> /by <date/time>.";
    private static final String EVENT_FROM_MARKER = "/from";
    private static final String EVENT_TO_MARKER = "/to";
    private static final String EVENT_FORMAT_ERROR = "Did you even read the event format? "
            + "Use: event <description> /from <start> /to <end>.";
    private static final String EVENT_DATE_ERROR = "Those dates won't do. Use something valid, "
            + "such as 2019-10-15 or 2/12/2019 1800.";

    /** Holds the validated fields extracted from an event command. */
    private record EventParts(String description, String fromInput, String toInput) {
    }

    /**
     * Parses a todo command into a todo task.
     *
     * @param input the complete todo command
     * @return the parsed todo task
     * @throws HertaException if the todo description is empty
     */
    Todo parseTodo(String input) throws HertaException {
        String description = getCommandArguments(input, CommandType.TODO);
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
    Deadline parseDeadline(String input) throws HertaException {
        String content = getCommandArguments(input, CommandType.DEADLINE);
        String[] deadlineParts = content.split("\\s+" + DEADLINE_MARKER + "\\s+", 2);
        if (deadlineParts.length != 2) {
            throw new HertaException(DEADLINE_FORMAT_ERROR);
        }

        String description = deadlineParts[0].trim();
        String byInput = deadlineParts[1].trim();
        if (description.isEmpty() || byInput.isEmpty()) {
            throw new HertaException(DEADLINE_FORMAT_ERROR);
        }

        String errorMessage = "That is not a date. Use a real one, such as "
                + "2019-10-15 or 2/12/2019 1800.";
        return new Deadline(description, parseUserDateTime(byInput, errorMessage));
    }

    /**
     * Parses an event command into an event task.
     *
     * @param input the complete event command
     * @return the parsed event task
     * @throws HertaException if the command format, date/time, or event range is invalid
     */
    Event parseEvent(String input) throws HertaException {
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
        String[] eventParts = content.split("\\s+" + EVENT_FROM_MARKER + "\\s+", 2);
        if (eventParts.length != 2) {
            throw new HertaException(EVENT_FORMAT_ERROR);
        }

        String[] timeParts = eventParts[1].split("\\s+" + EVENT_TO_MARKER + "\\s+", 2);
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
     * Parses a user-provided date/time and converts parsing failures into an explanation.
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
}
