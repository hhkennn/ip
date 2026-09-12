package herta.parser;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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
        assert input != null && commandType != null
                : "Command parsing requires input and an identified command type.";
        return switch (commandType) {
            case BYE -> new ExitCommand();
            case LIST -> new ListCommand();
            case FIND -> new FindCommand(parseFindKeyword(input));
            case TODO -> new TodoCommand(parseTodo(input));
            case DEADLINE -> new DeadlineCommand(parseDeadline(input));
            case EVENT -> new EventCommand(parseEvent(input));
            case DELETE -> new DeleteCommand(parseTaskIndex(input, commandType.getKeyword()));
            case MARK -> new MarkCommand(parseTaskIndex(input, commandType.getKeyword()));
            case UNMARK -> new UnmarkCommand(parseTaskIndex(input, commandType.getKeyword()));
            case ARCHIVE -> new ArchiveCommand(parseArchiveSelection(input));
            case ARCHIVED -> {
                if (!getCommandArguments(input, commandType).isEmpty()) {
                    throw new HertaException("Use: archived.");
                }
                yield new ArchivedCommand();
            }
            case RESTORE -> new RestoreCommand(parseRestoreTaskNumber(input));
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
        String[] parts = getCommandArguments(input, CommandType.FILTER).split("\\s+", 2);
        if (parts.length != 2 || !parts[0].equals(FILTER_DATE_MARKER)) {
            throw new HertaException("You forgot the " + FILTER_DATE_MARKER
                    + ". Use: filter " + FILTER_DATE_MARKER + " <date>.");
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
        assert days > 0 : "A parsed upcoming range must be positive.";
        return days;
    }

    /**
     * Validates a sort command.
     *
     * @param input the complete sort command
     * @throws HertaException if the command does not request date sorting
     */
    public void validateSortCommand(String input) throws HertaException {
        if (!input.equals(DATE_SORT_COMMAND)) {
            throw new HertaException("That is not a sorting option. Use: "
                    + DATE_SORT_COMMAND + ".");
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
