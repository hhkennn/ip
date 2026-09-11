package herta.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import herta.command.ArchiveCommand;
import herta.command.ArchivedCommand;
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
        assertInstanceOf(ArchiveCommand.class, parser.parse("archive 1-2"));
        assertInstanceOf(ArchivedCommand.class, parser.parse("archived"));
        assertInstanceOf(RestoreCommand.class, parser.parse("restore 1"));
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
        assertEquals("That is not a date. Use a real one, such as 2019-10-15 or 2/12/2019 1800.",
                invalidDate.getMessage());
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

        assertEquals("Time moves forward. Make the event end after it starts.", exception.getMessage());
    }

    @Test
    void parseEvent_invalidDateTime_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () -> parser.parseEvent(
                "event meeting /from someday /to 2019-10-16"));

        assertEquals("Those dates won't do. Use something valid, such as 2019-10-15 or 2/12/2019 1800.",
                exception.getMessage());
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

        assertEquals("You forgot the /on. Use: filter /on <date>.", exception.getMessage());
    }

    @Test
    void parseFilterDate_invalidDate_throwsHelpfulException() {
        HertaException exception = assertThrows(HertaException.class, () ->
                parser.parseFilterDate("filter /on 31/02/2019"));

        assertEquals("That date won't do. Try 2019-10-15 or 15/10/2019.", exception.getMessage());
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
            assertEquals("That range makes no sense. Use a positive number of days.",
                    exception.getMessage());
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
    void parseArchiveSelection_validSelectors_returnsRangesInInputOrder() throws HertaException {
        ArchiveSelection selection = parser.parseArchiveSelection("archive 02 2-2 3-5 4");

        assertFalse(selection.isAll());
        assertEquals(List.of(new ArchiveRange(2, 2), new ArchiveRange(2, 2),
                new ArchiveRange(3, 5), new ArchiveRange(4, 4)), selection.getRanges());
        assertEquals(new ArchiveRange(2, 2),
                parser.parseArchiveSelection("archive 0000000000000000000002").getRanges().get(0));
        assertTrue(parser.parseArchiveSelection("archive all").isAll());
    }

    @Test
    void parseArchiveSelection_invalidInput_usesSpecificSelectionErrors() {
        assertEquals("Use: archive <number> [<number> ...], archive <start>-<end>, or archive all.",
                assertArchiveError("archive").getMessage());
        assertEquals("That's not a valid task selection. Try: archive 1 3-5.",
                assertArchiveError("archive -1").getMessage());
        assertEquals("That's not a valid task selection. Try: archive 1 3-5.",
                assertArchiveError("archive 1 - 3").getMessage());
        assertEquals("That range makes no sense. Use an ascending range such as archive 2-5.",
                assertArchiveError("archive 5-2").getMessage());
        assertEquals("That's not a valid task selection. Try: archive 1 3-5.",
                assertArchiveError("archive 999999999999999999").getMessage());
        assertEquals("Use archive all by itself, or select task numbers and ranges.",
                assertArchiveError("archive all 1").getMessage());
    }

    @Test
    void parseRestoreTaskNumber_validAndInvalidInputs_areDistinguished() throws HertaException {
        assertEquals(0, parser.parseRestoreTaskNumber("restore 001"));
        assertEquals(0, parser.parseRestoreTaskNumber("restore 0000000000000000000001"));
        for (String input : List.of("restore", "restore 1 2", "restore 1-2", "restore -1",
                "restore +1", "restore 999999999999999999")) {
            HertaException exception = assertRestoreError(input);
            String expected = input.equals("restore")
                    ? "Use: restore <archived task number>."
                    : "That's not an archived task number. Try: restore 1.";
            assertEquals(expected, exception.getMessage());
        }
    }

    @Test
    void validateSortCommand_onlyAcceptsDateSorting() throws HertaException {
        parser.validateSortCommand("sort date");

        HertaException exception = assertThrows(HertaException.class, () ->
                parser.validateSortCommand("sort time"));
        assertEquals("That is not a sorting option. Use: sort date.", exception.getMessage());
    }

    private HertaException assertArchiveError(String input) {
        return assertThrows(HertaException.class, () -> parser.parseArchiveSelection(input));
    }

    private HertaException assertRestoreError(String input) {
        return assertThrows(HertaException.class, () -> parser.parseRestoreTaskNumber(input));
    }
}
