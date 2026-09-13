package herta.parser;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import herta.command.ArchiveCommand;
import herta.command.ArchivedCommand;
import herta.command.Command;
import herta.command.DeadlineCommand;
import herta.command.DeleteCommand;
import herta.command.EventCommand;
import herta.command.ExitCommand;
import herta.command.FilterCommand;
import herta.command.FindCommand;
import herta.command.ListCommand;
import herta.command.MarkCommand;
import herta.command.RestoreCommand;
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
 * Interprets user commands and delegates specialized argument parsing.
 */
public class Parser {
    private static final String FILTER_DATE_MARKER = "/on";
    private static final String DATE_SORT_COMMAND = "sort date";
    private static final String UPCOMING_RANGE_ERROR = "That range makes no sense. "
            + "Use a positive number of days.";
    private static final String NO_ARGUMENT_ERROR_PREFIX = "Use: ";
    private static final int MAX_NUMBER_LENGTH = 64;
    private static final int MAX_UPCOMING_DAYS = 4_000_000;

    private final TaskCreationParser taskCreationParser = new TaskCreationParser();
    private final ArchiveCommandParser archiveCommandParser = new ArchiveCommandParser();

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
        if (input == null || commandType == null) {
            throw new HertaException("Command parsing requires input and a command type.");
        }
        validateUnknownNoArgumentCommand(input, commandType);
        return createCommand(input, commandType);
    }

    /** Creates a command after its type and command-specific arguments are validated. */
    private Command createCommand(String input, CommandType commandType) throws HertaException {
        return switch (commandType) {
            case BYE -> createExitCommand(input, commandType);
            case LIST -> createListCommand(input, commandType);
            case FIND -> new FindCommand(parseFindKeyword(input));
            case TODO -> new TodoCommand(parseTodo(input));
            case DEADLINE -> new DeadlineCommand(parseDeadline(input));
            case EVENT -> new EventCommand(parseEvent(input));
            case DELETE -> new DeleteCommand(parseTaskIndex(input, commandType.getKeyword()));
            case MARK -> new MarkCommand(parseTaskIndex(input, commandType.getKeyword()));
            case UNMARK -> new UnmarkCommand(parseTaskIndex(input, commandType.getKeyword()));
            case ARCHIVE -> new ArchiveCommand(parseArchiveSelection(input));
            case ARCHIVED -> createArchivedCommand(input, commandType);
            case RESTORE -> new RestoreCommand(parseRestoreTaskNumber(input));
            case FILTER -> new FilterCommand(parseFilterDate(input));
            case UPCOMING -> new UpcomingCommand(parseUpcomingDays(input));
            case SORT -> createSortCommand(input);
            default -> new UnknownCommand(input);
        };
    }

    /** Creates the exit command after rejecting unexpected arguments. */
    private Command createExitCommand(String input, CommandType commandType) throws HertaException {
        validateNoArguments(input, commandType);
        return new ExitCommand();
    }

    /** Creates the list command after rejecting unexpected arguments. */
    private Command createListCommand(String input, CommandType commandType) throws HertaException {
        validateNoArguments(input, commandType);
        return new ListCommand();
    }

    /** Creates the archive-view command after rejecting unexpected arguments. */
    private Command createArchivedCommand(String input, CommandType commandType)
            throws HertaException {
        validateNoArguments(input, commandType);
        return new ArchivedCommand();
    }

    /** Creates the sort command after validating its sorting option. */
    private Command createSortCommand(String input) throws HertaException {
        validateSortCommand(input);
        return new SortCommand();
    }

    /**
     * Parses a todo command into a todo task.
     *
     * @param input the complete todo command
     * @return the parsed todo task
     * @throws HertaException if the todo description is empty
     */
    public Todo parseTodo(String input) throws HertaException {
        return taskCreationParser.parseTodo(input);
    }

    /**
     * Parses the keyword from a find command.
     *
     * @param input the complete find command
     * @return the keyword to search for
     * @throws HertaException if the keyword is empty
     */
    public String parseFindKeyword(String input) throws HertaException {
        String keyword = CommandType.FIND.extractArguments(input);
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
        return taskCreationParser.parseDeadline(input);
    }

    /**
     * Parses an event command into an event task.
     *
     * @param input the complete event command
     * @return the parsed event task
     * @throws HertaException if the command format, date/time, or event range is invalid
     */
    public Event parseEvent(String input) throws HertaException {
        return taskCreationParser.parseEvent(input);
    }

    /**
     * Parses the date from a filter command.
     *
     * @param input the complete filter command
     * @return the requested filter date
     * @throws HertaException if the command format or date is invalid
     */
    public LocalDate parseFilterDate(String input) throws HertaException {
        Objects.requireNonNull(input, "A filter command cannot be null.");
        String arguments = CommandType.FILTER.extractArguments(input);
        int markerCount = countMarker(arguments, FILTER_DATE_MARKER);
        if (markerCount > 1) {
            throw new HertaException("Parameter " + FILTER_DATE_MARKER
                    + " specified more than once.");
        }
        String[] filterParts = arguments.split("[ \\t]+", 2);
        if (markerCount == 0 || filterParts.length == 0
                || !filterParts[0].equals(FILTER_DATE_MARKER)) {
            throw new HertaException("You forgot the " + FILTER_DATE_MARKER
                    + ". Use: filter " + FILTER_DATE_MARKER + " <date>.");
        }
        if (filterParts.length == 1 || filterParts[1].isBlank()) {
            throw new HertaException("You forgot the date after " + FILTER_DATE_MARKER
                    + ". Use: filter " + FILTER_DATE_MARKER + " <date>.");
        }

        try {
            return DateTimeParser.parseUserDate(filterParts[1]);
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
        String daysInput = CommandType.UPCOMING.extractArguments(input);
        final int days;
        if (!daysInput.matches("[0-9]+") || daysInput.length() > MAX_NUMBER_LENGTH) {
            throw new HertaException(UPCOMING_RANGE_ERROR);
        }
        try {
            days = Integer.parseInt(daysInput);
        } catch (NumberFormatException e) {
            throw new HertaException(UPCOMING_RANGE_ERROR);
        }
        if (days <= 0 || days > MAX_UPCOMING_DAYS) {
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
        if (!CommandType.SORT.extractArguments(input).equals("date")) {
            throw new HertaException("That is not a sorting option. Use: "
                    + DATE_SORT_COMMAND + ".");
        }
    }

    /**
     * Parses a one-based task number from a task-selection command.
     *
     * @param input the complete task-selection command
     * @param commandKeyword the command keyword used in the input
     * @return the corresponding zero-based task index
     * @throws HertaException if the task number is not numeric
     */
    public int parseTaskIndex(String input, String commandKeyword) throws HertaException {
        if (input == null || commandKeyword == null) {
            throw new HertaException("That's not a task number. Try: "
                    + commandKeyword + " 1.");
        }
        String normalizedInput = input.trim();
        if (!hasTaskNumberPrefix(normalizedInput, commandKeyword)) {
            throw new HertaException("That's not a task number. Try: " + commandKeyword + " 1.");
        }
        String taskNumber = normalizedInput.substring(commandKeyword.length()).trim();
        if (!isPositiveDecimal(taskNumber)) {
            throw new HertaException("That's not a task number. Try: " + commandKeyword + " 1.");
        }
        try {
            return new BigInteger(taskNumber).intValueExact() - 1;
        } catch (ArithmeticException e) {
            throw new HertaException("That's not a task number. Try: " + commandKeyword + " 1.");
        }
    }

    /** Checks that a task number follows its command keyword with a supported separator. */
    private boolean hasTaskNumberPrefix(String input, String commandKeyword) {
        return input.startsWith(commandKeyword)
                && input.length() > commandKeyword.length()
                && CommandTokenizer.isHorizontalWhitespace(input.charAt(commandKeyword.length()));
    }

    /** Rejects trailing input for commands whose grammar has no arguments. */
    private void validateNoArguments(String input, CommandType commandType) throws HertaException {
        if (!commandType.extractArguments(input).isEmpty()) {
            throw new HertaException(NO_ARGUMENT_ERROR_PREFIX + commandType.getKeyword() + ".");
        }
    }

    /** Gives no-argument commands a usage error even when their extra text prevents recognition. */
    private void validateUnknownNoArgumentCommand(String input, CommandType commandType)
            throws HertaException {
        if (commandType != CommandType.UNKNOWN) {
            return;
        }
        String normalizedInput = input.trim();
        String firstToken = normalizedInput.split("[ \\t]+", 2)[0];
        for (CommandType supportedCommand : CommandType.values()) {
            String keyword = supportedCommand.getKeyword();
            if (!keyword.isEmpty() && firstToken.equalsIgnoreCase(keyword)
                    && !firstToken.equals(keyword)) {
                throw new HertaException("Commands are lowercase. Try: " + keyword + ".");
            }
        }
        for (String keyword : new String[] {"list", "bye"}) {
            if (hasTrailingArguments(normalizedInput, keyword)) {
                throw new HertaException(NO_ARGUMENT_ERROR_PREFIX + keyword + ".");
            }
        }
    }

    /** Identifies trailing text after a no-argument command using supported separators. */
    private boolean hasTrailingArguments(String input, String keyword) {
        return input.startsWith(keyword)
                && input.length() > keyword.length()
                && CommandTokenizer.isHorizontalWhitespace(input.charAt(keyword.length()));
    }

    /** Checks a bounded, positive decimal number before constructing a BigInteger. */
    private boolean isPositiveDecimal(String input) {
        if (!input.matches("[0-9]+") || input.length() > MAX_NUMBER_LENGTH) {
            return false;
        }
        try {
            return new BigInteger(input).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Counts a marker only when it is a complete token. */
    private int countMarker(String input, String marker) {
        int count = 0;
        int searchStart = 0;
        while (searchStart < input.length()) {
            int markerIndex = input.indexOf(marker, searchStart);
            if (markerIndex < 0) {
                break;
            }
            int markerEnd = markerIndex + marker.length();
            boolean hasValidLeftBoundary = markerIndex == 0
                    || CommandTokenizer.isHorizontalWhitespace(input.charAt(markerIndex - 1));
            boolean hasValidRightBoundary = markerEnd == input.length()
                    || CommandTokenizer.isHorizontalWhitespace(input.charAt(markerEnd));
            if (hasValidLeftBoundary && hasValidRightBoundary) {
                count++;
            }
            searchStart = markerIndex + 1;
        }
        return count;
    }

    /**
     * Parses the selectors from an archive command.
     *
     * @param input the complete archive command
     * @return the validated archive selection
     * @throws HertaException if the selectors are malformed or out of integer range
     */
    public ArchiveSelection parseArchiveSelection(String input) throws HertaException {
        return archiveCommandParser.parseArchiveSelection(input);
    }

    /**
     * Parses the single archive task number from a restore command.
     *
     * @param input the complete restore command
     * @return the selected archive task number as a zero-based index
     * @throws HertaException if the argument is invalid
     */
    public int parseRestoreTaskNumber(String input) throws HertaException {
        return archiveCommandParser.parseRestoreTaskNumber(input);
    }
}
