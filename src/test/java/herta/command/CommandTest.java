package herta.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.TaskList;
import herta.task.Todo;
import herta.ui.Ui;

/**
 * Tests command execution, persistence coordination, task queries, and command errors.
 */
class CommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void addCommand_executePersistsAndAddsTask() throws Exception {
        Path dataFile = temporaryDirectory.resolve("tasks.txt");
        TaskList tasks = new TaskList();
        Todo todo = new Todo("read book");

        String output = captureOutput(() ->
                new TodoCommand(todo).execute(tasks, new Ui(), new Storage(dataFile.toString())));

        assertEquals(1, tasks.size());
        assertSame(todo, tasks.get(0));
        assertEquals(List.of("T | 0 | read book"), Files.readAllLines(dataFile));
        assertTrue(output.contains("There. I've added it:"));
        assertTrue(output.contains("[T][ ] read book"));
        assertTrue(output.contains("That makes 1 task."));
    }

    @Test
    void addCommand_subclassesPersistDeadlineAndEvent() throws Exception {
        Path dataFile = temporaryDirectory.resolve("typed-tasks.txt");
        TaskList tasks = new TaskList();
        Deadline deadline = new Deadline("submit report",
                LocalDateTime.of(2019, 10, 15, 18, 0));
        Event event = new Event("project meeting",
                LocalDateTime.of(2019, 10, 16, 10, 0),
                LocalDateTime.of(2019, 10, 16, 11, 0));
        Storage storage = new Storage(dataFile.toString());

        captureOutput(() ->
                new DeadlineCommand(deadline).execute(tasks, new Ui(), storage));
        captureOutput(() ->
                new EventCommand(event).execute(tasks, new Ui(), storage));

        assertEquals(2, tasks.size());
        assertEquals(List.of(deadline.toStorageString(), event.toStorageString()),
                Files.readAllLines(dataFile));
    }

    @Test
    void markUnmarkAndDelete_executeUpdatesPersistenceAndTaskList() throws Exception {
        Path dataFile = temporaryDirectory.resolve("tasks.txt");
        Todo todo = new Todo("read book");
        TaskList tasks = new TaskList(List.of(todo));
        Storage storage = new Storage(dataFile.toString());

        String markOutput = captureOutput(() ->
                new MarkCommand(0).execute(tasks, new Ui(), storage));
        assertTrue(todo.isDone());
        assertTrue(markOutput.contains("There. It's marked complete:"));

        String unmarkOutput = captureOutput(() ->
                new UnmarkCommand(0).execute(tasks, new Ui(), storage));
        assertFalse(todo.isDone());
        assertTrue(unmarkOutput.contains("As you wish. It's incomplete again:"));

        String deleteOutput = captureOutput(() ->
                new DeleteCommand(0).execute(tasks, new Ui(), storage));
        assertEquals(0, tasks.size());
        assertTrue(deleteOutput.contains("There. It's gone:"));
        assertEquals(List.of(), Files.readAllLines(dataFile));
    }

    @Test
    void taskChangingCommands_saveFailureRestoresOrPreservesMemory() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("not-a-file");
        Files.createDirectory(dataDirectory);
        Storage failingStorage = new Storage(dataDirectory.toString());

        Todo todo = new Todo("read book");
        TaskList tasks = new TaskList(List.of(todo));

        assertThrows(HertaException.class, () ->
                new MarkCommand(0).execute(tasks, new Ui(), failingStorage));
        assertFalse(todo.isDone());

        todo.markAsDone();
        assertThrows(HertaException.class, () ->
                new UnmarkCommand(0).execute(tasks, new Ui(), failingStorage));
        assertTrue(todo.isDone());

        assertThrows(HertaException.class, () ->
                new DeleteCommand(0).execute(tasks, new Ui(), failingStorage));
        assertEquals(1, tasks.size());
        assertSame(todo, tasks.get(0));

        TaskList invalidAddTasks = new TaskList();
        assertThrows(HertaException.class, () ->
                new TodoCommand(new Todo("contains | separator"))
                        .execute(invalidAddTasks, new Ui(), failingStorage));
        assertEquals(0, invalidAddTasks.size());

        HertaException invalidIndexException = assertThrows(HertaException.class, () ->
                new MarkCommand(1).execute(tasks, new Ui(), failingStorage));
        assertEquals("That task doesn't exist. Did you even check the list?",
                invalidIndexException.getMessage());
    }

    @Test
    void listFilterAndSort_executeDisplayExpectedTaskSelections() throws Exception {
        Todo todo = new Todo("buy milk");
        Deadline deadline = new Deadline("submit report",
                LocalDateTime.of(2019, 10, 15, 18, 0));
        Event event = new Event("project meeting",
                LocalDateTime.of(2019, 10, 14, 23, 0),
                LocalDateTime.of(2019, 10, 16, 1, 0));
        TaskList tasks = new TaskList(List.of(todo, deadline, event));

        String listOutput = captureOutput(() ->
                new ListCommand().execute(tasks, new Ui(), null));
        assertTrue(listOutput.contains("1.[T][ ] buy milk"));
        assertTrue(listOutput.contains("2.[D][ ] submit report"));
        assertTrue(listOutput.contains("3.[E][ ] project meeting"));

        String filterOutput = captureOutput(() ->
                new FilterCommand(LocalDate.of(2019, 10, 15))
                        .execute(tasks, new Ui(), null));
        assertTrue(filterOutput.contains("Tasks occurring on Oct 15 2019:"));
        assertTrue(filterOutput.contains("2.[D][ ] submit report"));
        assertTrue(filterOutput.contains("3.[E][ ] project meeting"));
        assertFalse(filterOutput.contains("1.[T][ ] buy milk"));

        String findOutput = captureOutput(() ->
                new FindCommand("REPORT").execute(tasks, new Ui(), null));
        assertTrue(findOutput.contains("Here are the matching tasks in your list:"));
        assertTrue(findOutput.contains("2.[D][ ] submit report"));
        assertFalse(findOutput.contains("1.[T][ ] buy milk"));
        assertFalse(findOutput.contains("3.[E][ ] project meeting"));

        String sortOutput = captureOutput(() ->
                new SortCommand().execute(tasks, new Ui(), null));
        assertTrue(sortOutput.contains("Here are your tasks sorted by date:"));
        assertTrue(sortOutput.indexOf("3.[E][ ] project meeting")
                < sortOutput.indexOf("2.[D][ ] submit report"));
        assertTrue(sortOutput.indexOf("2.[D][ ] submit report")
                < sortOutput.indexOf("1.[T][ ] buy milk"));
        assertSame(todo, tasks.get(0));
    }

    @Test
    void filterCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        TaskList tasks = new TaskList(List.of(new Todo("buy milk")));

        String output = captureOutput(() ->
                new FilterCommand(LocalDate.of(2019, 10, 15))
                        .execute(tasks, new Ui(), null));

        assertTrue(output.contains("No deadlines or events occur on Oct 15 2019."));
    }

    @Test
    void findCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        String output = captureOutput(() ->
                new FindCommand("missing")
                        .execute(new TaskList(List.of(new Todo("buy milk"))), new Ui(), null));

        assertTrue(output.contains("No tasks match the keyword: missing"));
    }

    @Test
    void upcomingCommand_executeShowsOnlyIncompleteFutureTasks() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Deadline upcoming = new Deadline("upcoming report", now.plusDays(1));
        Deadline completed = new Deadline("completed report", now.plusDays(1));
        completed.markAsDone();
        TaskList tasks = new TaskList(List.of(new Todo("buy milk"), upcoming, completed));

        String output = captureOutput(() ->
                new UpcomingCommand(2).execute(tasks, new Ui(), null));

        assertTrue(output.contains("Upcoming deadlines and events in the next 2 days:"));
        assertTrue(output.contains("2.[D][ ] upcoming report"));
        assertFalse(output.contains("buy milk"));
        assertFalse(output.contains("completed report"));
    }

    @Test
    void upcomingCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        String output = captureOutput(() ->
                new UpcomingCommand(2).execute(
                        new TaskList(List.of(new Todo("buy milk"))), new Ui(), null));

        assertTrue(output.contains("No incomplete deadlines or events are upcoming in the next "
                + "2 days."));
    }

    @Test
    void unknownCommand_executeReportsEmptyAndInvalidInputs() {
        HertaException emptyException = assertThrows(HertaException.class, () ->
                new UnknownCommand("").execute(null, null, null));
        HertaException invalidException = assertThrows(HertaException.class, () ->
                new UnknownCommand("blah").execute(null, null, null));

        assertEquals("Nothing? Were you expecting me to read your mind?",
                emptyException.getMessage());
        assertTrue(invalidException.getMessage().startsWith(
                "That command is invalid. Were you just guessing?"));
    }

    @Test
    void exitCommand_executeDisplaysGoodbyeAndMarksExit() throws Exception {
        ExitCommand command = new ExitCommand();

        String output = captureOutput(() -> command.execute(null, new Ui(), null));

        assertTrue(command.isExit());
        assertTrue(output.contains("Leaving already? Goodbye."));
    }

    @Test
    void nonExitCommand_usesDefaultExitStatus() {
        assertFalse(new ListCommand().isExit());
    }

    private String captureOutput(OutputAction action) throws Exception {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output));
            action.run();
            return output.toString();
        } finally {
            System.setOut(originalOutput);
        }
    }

    @FunctionalInterface
    private interface OutputAction {
        void run() throws Exception;
    }
}
