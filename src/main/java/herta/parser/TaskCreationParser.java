package herta.parser;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import herta.exception.HertaException;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.TaskDescriptionValidator;
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
        String description = CommandType.TODO.extractArguments(input);
        if (description.isEmpty()) {
            throw new HertaException("A blank todo? Even I can't organise nothing. "
                    + "Use: todo <description>.");
        }
        validateDescription(description);
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
        String deadlineArguments = CommandType.DEADLINE.extractArguments(input);
        int markerCount = countMarker(deadlineArguments, DEADLINE_MARKER);
        if (markerCount == 0) {
            throw new HertaException(DEADLINE_FORMAT_ERROR);
        }
        if (markerCount > 1) {
            throw duplicateParameterError(DEADLINE_MARKER);
        }

        int markerIndex = findMarker(deadlineArguments, DEADLINE_MARKER);
        String description = deadlineArguments.substring(0, markerIndex).trim();
        String byInput = deadlineArguments.substring(markerIndex + DEADLINE_MARKER.length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("A deadline needs a description. Use: deadline <description> "
                    + "/by <date/time>.");
        }
        if (byInput.isEmpty()) {
            throw new HertaException("A deadline needs a value after /by. Use: deadline "
                    + "<description> /by <date/time>.");
        }
        validateDescription(description);

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
        String eventArguments = CommandType.EVENT.extractArguments(input);
        validateEventMarkers(eventArguments);
        return extractEventParts(eventArguments);
    }

    /** Validates event marker presence and uniqueness before extracting any fields. */
    private void validateEventMarkers(String eventArguments) throws HertaException {
        int fromCount = countMarker(eventArguments, EVENT_FROM_MARKER);
        int toCount = countMarker(eventArguments, EVENT_TO_MARKER);
        if (fromCount > 1) {
            throw duplicateParameterError(EVENT_FROM_MARKER);
        }
        if (toCount > 1) {
            throw duplicateParameterError(EVENT_TO_MARKER);
        }
        if (fromCount == 0) {
            throw new HertaException("You forgot the /from marker. "
                    + "Use: event <description> /from <start> /to <end>.");
        }
        if (toCount == 0) {
            throw new HertaException("You forgot the /to marker. "
                    + "Use: event <description> /from <start> /to <end>.");
        }
    }

    /** Extracts the event description and both date/time values from validated arguments. */
    private EventParts extractEventParts(String eventArguments) throws HertaException {
        int fromIndex = findMarker(eventArguments, EVENT_FROM_MARKER);
        String description = eventArguments.substring(0, fromIndex).trim();
        String remainder = eventArguments.substring(fromIndex + EVENT_FROM_MARKER.length());
        int toIndex = findMarker(remainder, EVENT_TO_MARKER);
        if (toIndex < 0) {
            throw new HertaException(EVENT_FORMAT_ERROR);
        }

        String fromInput = remainder.substring(0, toIndex).trim();
        String toInput = remainder.substring(toIndex + EVENT_TO_MARKER.length()).trim();
        if (description.isEmpty()) {
            throw new HertaException("An event needs a description. Use: event <description> "
                    + "/from <start> /to <end>.");
        }
        if (fromInput.isEmpty()) {
            throw new HertaException("An event needs a value after /from. Use: event "
                    + "<description> /from <start> /to <end>.");
        }
        if (toInput.isEmpty()) {
            throw new HertaException("An event needs a value after /to. Use: event "
                    + "<description> /from <start> /to <end>.");
        }
        validateDescription(description);
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
            return DateTimeParser.parseUserDateTime(input);
        } catch (DateTimeParseException e) {
            throw new HertaException(errorMessage);
        }
    }

    /** Validates a task description and turns domain validation into usage guidance. */
    private void validateDescription(String description) throws HertaException {
        try {
            TaskDescriptionValidator.validate(description);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new HertaException(e.getMessage());
        }
    }

    /** Counts marker tokens rather than marker-like text embedded in a word. */
    private int countMarker(String input, String marker) {
        int markerCount = 0;
        int searchStart = 0;
        while (searchStart < input.length()) {
            int markerIndex = findMarker(input, marker, searchStart);
            if (markerIndex < 0) {
                return markerCount;
            }
            markerCount++;
            searchStart = markerIndex + marker.length();
        }
        return markerCount;
    }

    /** Finds the first marker token after the supplied character offset. */
    private int findMarker(String input, String marker) {
        return findMarker(input, marker, 0);
    }

    /** Finds a marker token whose surrounding characters are separators or boundaries. */
    private int findMarker(String input, String marker, int searchStart) {
        int markerIndex = input.indexOf(marker, searchStart);
        while (markerIndex >= 0) {
            int markerEnd = markerIndex + marker.length();
            boolean hasValidLeftBoundary = markerIndex == 0
                    || CommandTokenizer.isHorizontalWhitespace(input.charAt(markerIndex - 1));
            boolean hasValidRightBoundary = markerEnd == input.length()
                    || CommandTokenizer.isHorizontalWhitespace(input.charAt(markerEnd));
            if (hasValidLeftBoundary && hasValidRightBoundary) {
                return markerIndex;
            }
            markerIndex = input.indexOf(marker, markerIndex + 1);
        }
        return -1;
    }

    /** Returns the stable message used for repeated command parameters. */
    private HertaException duplicateParameterError(String marker) {
        return new HertaException("Parameter " + marker + " specified more than once.");
    }

}
