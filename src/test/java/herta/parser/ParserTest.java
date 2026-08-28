package herta.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

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
 * Tests conversion of user command text into validated domain values and commands.
 */
class ParserTest {
    private final Parser parser = new Parser();

    @Test
    void parse_supportedCommands_returnsCorrespondingCommandObjects() throws HertaException {
        assertInstanceOf(TodoCommand.class, parser.parse("todo read book"));
        assertInstanceOf(DeadlineCommand.class,
                parser.parse("deadline submit report /by 2019-10-15"));
        assertInstanceOf(EventCommand.class,
                parser.parse("event meeting /from 2019-10-15 /to 2019-10-16"));
        assertInstanceOf(ListCommand.class, parser.parse("list"));
        assertInstanceOf(FindCommand.class, parser.parse("find book"));
        assertInstanceOf(FilterCommand.class, parser.parse("filter /on 2019-10-15"));
        assertInstanceOf(UpcomingCommand.class, parser.parse("upcoming 7"));
        assertInstanceOf(SortCommand.class, parser.parse("sort date"));
        assertInstanceOf(MarkCommand.class, parser.parse("mark 1"));
        assertInstanceOf(UnmarkCommand.class, parser.parse("unmark 1"));
        assertInstanceOf(DeleteCommand.class, parser.parse("delete 1"));
        assertInstanceOf(ExitCommand.class, parser.parse("bye"));
        assertInstanceOf(UnknownCommand.class, parser.parse("unknown command"));
    }

    @Test
    void parseCommandType_delegatesCommandRecognition() {
        assertEquals(CommandType.SORT, parser.parseCommandType("sort date"));
        assertEquals(CommandType.SORT, parser.parseCommandType("sort time"));
    }

    @Test
    void parseTodo_nonEmptyDescription_returnsTodo() throws HertaException {
        Todo todo = parser.parseTodo("todo   read book");

        assertEquals("read book", todo.getDescription());
        assertEquals("T | 0 | read book", todo.toStorageString());
    }

    @Test
    void parseTodo_emptyDescription_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () ->
                parser.parseTodo("todo   "));

        assertEquals("A blank todo? Even I can't organise nothing. Use: todo <description>.",
                exception.getMessage());
    }

    @Test
    void parseFindKeyword_nonEmptyKeyword_returnsTrimmedKeyword() throws HertaException {
        assertEquals("read book", parser.parseFindKeyword("find   read book  "));
    }

    @Test
    void parseFindKeyword_emptyKeyword_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () ->
                parser.parseFindKeyword("find   "));

        assertEquals("A blank search? Use: find <keyword>.", exception.getMessage());
    }

    @Test
    void parseDeadline_validInput_returnsDeadlineWithParsedDateTime() throws HertaException {
        Deadline deadline = parser.parseDeadline("deadline submit report /by 2/12/2019 1800");

        assertEquals("submit report", deadline.getDescription());
        assertEquals(LocalDateTime.of(2019, 12, 2, 18, 0), deadline.getBy());
    }

    @Test
    void parseDeadline_malformedInput_throwsHelpfulException() {
        HertaException missingDelimiter = assertThrows(HertaException.class, () ->
                parser.parseDeadline("deadline submit report"));
        HertaException invalidDate = assertThrows(HertaException.class, () ->
                parser.parseDeadline("deadline submit report /by 31/02/2019 1800"));

        assertEquals("Did you even read the deadline format? Use: deadline <description> /by <date/time>.",
                missingDelimiter.getMessage());
        assertEquals("Invalid deadline date/time. Use a date such as 2019-10-15 or a date/time such as "
                + "2/12/2019 1800.", invalidDate.getMessage());
    }

    @Test
    void parseEvent_validInput_returnsEventWithParsedRange() throws HertaException {
        Event event = parser.parseEvent(
                "event project meeting /from 2019-10-15 /to 2019-10-16");

        assertEquals("project meeting", event.getDescription());
        assertEquals(LocalDateTime.of(2019, 10, 15, 0, 0), event.getFrom());
        assertEquals(LocalDateTime.of(2019, 10, 16, 0, 0), event.getTo());
    }

    @Test
    void parseEvent_nonIncreasingRange_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () -> parser.parseEvent(
                "event meeting /from 2019-10-16 /to 2019-10-15"));

        assertEquals("An event must end after it starts.", exception.getMessage());
    }

    @Test
    void parseFilterDate_supportedFormats_returnsExpectedDate() throws HertaException {
        assertEquals(LocalDate.of(2019, 10, 15),
                parser.parseFilterDate("filter /on 2019-10-15"));
        assertEquals(LocalDate.of(2019, 10, 15),
                parser.parseFilterDate("filter /on 15/10/2019"));
    }

    @Test
    void parseFilterDate_missingOnKeyword_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () ->
                parser.parseFilterDate("filter 2019-10-15"));

        assertEquals("Use: filter /on <date>.", exception.getMessage());
    }

    @Test
    void parseUpcomingDays_positiveNumber_returnsNumber() throws HertaException {
        assertEquals(30, parser.parseUpcomingDays("upcoming 30"));
    }

    @Test
    void parseUpcomingDays_nonPositiveOrNonNumericInput_throwsHelpfulException() {
        for (String input : new String[] {"upcoming 0", "upcoming -1", "upcoming many"}) {
            HertaException exception = assertThrows(HertaException.class, () ->
                    parser.parseUpcomingDays(input));
            assertEquals("Use: upcoming <positive number of days>.", exception.getMessage());
        }
    }

    @Test
    void parseTaskIndex_oneBasedInput_returnsZeroBasedIndex() throws HertaException {
        assertEquals(0, parser.parseTaskIndex("mark 1", "mark"));
        assertEquals(2, parser.parseTaskIndex("delete 3", "delete"));
    }

    @Test
    void parseTaskIndex_nonNumericInput_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () ->
                parser.parseTaskIndex("delete nope", "delete"));

        assertEquals("That's not a task number. Try: delete 1.", exception.getMessage());
    }

    @Test
    void validateSortCommand_onlyAcceptsDateSorting() throws HertaException {
        parser.validateSortCommand("sort date");

        HertaException exception = assertThrows(HertaException.class, () ->
                parser.validateSortCommand("sort time"));
        assertEquals("Use: sort date.", exception.getMessage());
    }
}
