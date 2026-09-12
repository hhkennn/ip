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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import herta.Herta;
import herta.HertaResponse;
import herta.ResponseCategory;
import herta.exception.HertaException;
import herta.parser.ArchiveRange;
import herta.parser.ArchiveSelection;
import herta.storage.Storage;
import herta.storage.TaskRepository;
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
        assertTrue(todo.isCompleted());
        assertTrue(markOutput.contains("There. It's marked complete:"));

        String unmarkOutput = captureOutput(() ->
                new UnmarkCommand(0).execute(tasks, new Ui(), storage));
        assertFalse(todo.isCompleted());
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
        assertFalse(todo.isCompleted());

        todo.markAsDone();
        assertThrows(HertaException.class, () ->
                new UnmarkCommand(0).execute(tasks, new Ui(), failingStorage));
        assertTrue(todo.isCompleted());

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
        assertTrue(filterOutput.contains(
                "Here is what your schedule has for Oct 15 2019, if anything:"));
        assertTrue(filterOutput.contains("2.[D][ ] submit report"));
        assertTrue(filterOutput.contains("3.[E][ ] project meeting"));
        assertFalse(filterOutput.contains("1.[T][ ] buy milk"));

        String findOutput = captureOutput(() ->
                new FindCommand("REPORT").execute(tasks, new Ui(), null));
        assertTrue(findOutput.contains(
                "Looking for something? How predictable. Here are the matches:"));
        assertTrue(findOutput.contains("2.[D][ ] submit report"));
        assertFalse(findOutput.contains("1.[T][ ] buy milk"));
        assertFalse(findOutput.contains("3.[E][ ] project meeting"));

        String sortOutput = captureOutput(() ->
                new SortCommand().execute(tasks, new Ui(), null));
        assertTrue(sortOutput.contains("There. Your tasks are in date order."));
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

        assertTrue(output.contains("Nothing scheduled. A remarkably empty date."));
    }

    @Test
    void findCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        String output = captureOutput(() ->
                new FindCommand("missing")
                        .execute(new TaskList(List.of(new Todo("buy milk"))), new Ui(), null));

        assertTrue(output.contains(
                "I found nothing. Perhaps the task was only in your imagination."));
        assertFalse(output.contains(
                "Looking for something? How predictable. Here are the matches:"));
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

        assertTrue(output.contains("Your next 2 days. Try not to fall behind:"));
        assertTrue(output.contains("2.[D][ ] upcoming report"));
        assertFalse(output.contains("buy milk"));
        assertFalse(output.contains("completed report"));
    }

    @Test
    void upcomingCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        String output = captureOutput(() ->
                new UpcomingCommand(2).execute(
                        new TaskList(List.of(new Todo("buy milk"))), new Ui(), null));

        assertTrue(output.contains("Nothing upcoming. Enjoy the silence while it lasts."));
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
    void archiveCommand_archiveCompletedTasks_preservesActiveOrderAndPersistsBothLists()
            throws Exception {
        Path activeFile = temporaryDirectory.resolve("data").resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());

        herta.getResponse("todo first");
        herta.getResponse("todo second");
        herta.getResponse("todo third");
        herta.getResponse("mark 1");
        herta.getResponse("mark 2");
        herta.getResponse("mark 3");

        HertaResponse response = herta.getResponse("archive 3 1-2");

        assertEquals(ResponseCategory.ARCHIVE, response.getResponseCategory());
        assertEquals("There. I've archived 3 completed tasks:\n"
                + "  [T][X] first\n"
                + "  [T][X] second\n"
                + "  [T][X] third\n"
                + "That leaves 0 active tasks. Try to keep up.",
                response.getMessage().replace(System.lineSeparator(), "\n"));
        assertEquals(List.of(), Files.readAllLines(activeFile));
        assertEquals(List.of("T | 1 | first", "T | 1 | second", "T | 1 | third"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void archiveCommand_incompleteSelection_rejectsAtomically() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo complete");
        herta.getResponse("todo incomplete");
        herta.getResponse("mark 1");

        HertaResponse response = herta.getResponse("archive 1 2");

        assertEquals(ResponseCategory.ERROR, response.getResponseCategory());
        assertEquals("Only completed tasks can be archived. Mark the task complete first.",
                response.getMessage());
        assertEquals(List.of("T | 1 | complete", "T | 0 | incomplete"),
                Files.readAllLines(activeFile));
        assertTrue(Files.notExists(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void archiveAll_skipsIncompleteTasksAndReportsNoOpCases() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo complete");
        herta.getResponse("todo incomplete");
        herta.getResponse("mark 1");

        HertaResponse archiveResponse = herta.getResponse("archive all");
        assertEquals(ResponseCategory.ARCHIVE, archiveResponse.getResponseCategory());
        assertTrue(archiveResponse.getMessage().contains("There. I've archived 1 completed task:"));
        assertEquals(List.of("T | 0 | incomplete"), Files.readAllLines(activeFile));

        HertaResponse noOpResponse = herta.getResponse("archive all");
        assertEquals(ResponseCategory.ARCHIVE, noOpResponse.getResponseCategory());
        assertEquals("Nothing to archive. There are no completed active tasks.",
                noOpResponse.getMessage());
        assertEquals(List.of("T | 1 | complete"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void archivedAndRestoreCommands_useIndependentArchiveStateAndPreserveStatus() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        new Storage(activeFile.toString()).save(new TaskList());
        Todo completed = new Todo("completed");
        completed.markAsDone();
        Todo incomplete = new Todo("incomplete");
        new Storage(archiveFile.toString()).save(new TaskList(List.of(completed, incomplete)));

        Herta herta = new Herta(activeFile.toString());
        HertaResponse viewResponse = herta.getResponse("archived");
        HertaResponse restoreResponse = herta.getResponse("restore 2");

        assertEquals(ResponseCategory.QUERY, viewResponse.getResponseCategory());
        assertEquals("Here are the tasks you've archived:\n"
                + "1.[T][X] completed\n"
                + "2.[T][ ] incomplete", viewResponse.getMessage().replace(System.lineSeparator(), "\n"));
        assertEquals(ResponseCategory.RESTORE, restoreResponse.getResponseCategory());
        assertEquals("There. I've restored it:\n"
                + "  [T][ ] incomplete\n"
                + "That makes 1 active task. Try to keep up.",
                restoreResponse.getMessage().replace(System.lineSeparator(), "\n"));
        assertEquals(List.of("T | 0 | incomplete"), Files.readAllLines(activeFile));
        assertEquals(List.of("T | 1 | completed"), Files.readAllLines(archiveFile));

        HertaResponse markResponse = herta.getResponse("mark 1");
        assertEquals(ResponseCategory.MARK, markResponse.getResponseCategory());
        assertEquals("T | 1 | incomplete", Files.readString(activeFile).trim());
    }

    @Test
    void archiveCommand_invalidBoundsTakePrecedenceOverIncompleteStatus() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo incomplete");
        HertaResponse response = herta.getResponse("archive 1 2");

        assertEquals("That task doesn't exist. Did you even check the list?", response.getMessage());
        assertEquals(List.of("T | 0 | incomplete"), Files.readAllLines(activeFile));
    }

    @Test
    void archiveCommand_afterSort_usesUnderlyingActiveOrder() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo unscheduled");
        herta.getResponse("deadline dated /by 2019-10-15");
        herta.getResponse("mark 1");
        herta.getResponse("mark 2");
        herta.getResponse("sort date");

        HertaResponse response = herta.getResponse("archive 1");

        assertTrue(response.getMessage().contains("[T][X] unscheduled"));
        assertEquals(List.of("D | 1 | dated | 2019-10-15T00:00:00"),
                Files.readAllLines(activeFile));
        assertEquals(List.of("T | 1 | unscheduled"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void restoreCommand_preservesTypedTaskDataAndCompletionStatus() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        new Storage(activeFile.toString()).save(new TaskList());
        Deadline deadline = new Deadline("deadline", LocalDateTime.of(2019, 10, 15, 18, 0));
        deadline.markAsDone();
        Event event = new Event("event", LocalDateTime.of(2019, 10, 16, 10, 0),
                LocalDateTime.of(2019, 10, 16, 11, 0));
        new Storage(archiveFile.toString()).save(new TaskList(List.of(deadline, event)));

        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("restore 1");
        herta.getResponse("restore 1");

        assertEquals(List.of(deadline.toStorageString(), event.toStorageString()),
                Files.readAllLines(activeFile));
        assertTrue(herta.getResponse("list").getMessage().contains("[D][X] deadline"));
        assertTrue(herta.getResponse("list").getMessage().contains("[E][ ] event"));
    }

    @Test
    void restoreCommand_persistenceFailureLeavesBothCollectionsUnchanged() throws Exception {
        Path activeDirectory = temporaryDirectory.resolve("active-directory");
        Path archiveFile = temporaryDirectory.resolve("archive.txt");
        Files.createDirectory(activeDirectory);
        Todo archivedTask = new Todo("archived");
        new Storage(archiveFile.toString()).save(new TaskList(List.of(archivedTask)));
        TaskList activeTasks = new TaskList();
        TaskList archivedTasks = new TaskList(List.of(archivedTask));
        TaskRepository repository = new TaskRepository(new Storage(activeDirectory.toString()),
                new Storage(archiveFile.toString()), activeTasks, archivedTasks);

        RestoreCommand restoreCommand = new RestoreCommand(0);
        Executable action = () -> executeRestoreCommand(restoreCommand, repository);
        HertaException exception = assertThrows(HertaException.class, action);

        assertTrue(exception.getMessage().startsWith("Failed to restore task: "));
        assertEquals(0, activeTasks.size());
        assertEquals(1, archivedTasks.size());
        assertEquals(List.of("T | 0 | archived"), Files.readAllLines(archiveFile));
    }

    @Test
    void archiveCommand_persistenceFailureLeavesBothCollectionsUnchanged() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Path archiveDirectory = temporaryDirectory.resolve("archive-directory");
        Files.writeString(activeFile, "T | 1 | completed\n");
        Files.createDirectory(archiveDirectory);
        Todo completed = new Todo("completed");
        completed.markAsDone();
        TaskList activeTasks = new TaskList(List.of(completed));
        TaskList archivedTasks = new TaskList();
        TaskRepository repository = new TaskRepository(new Storage(activeFile.toString()),
                new Storage(archiveDirectory.toString()), activeTasks, archivedTasks);

        ArchiveCommand archiveCommand = new ArchiveCommand(
                new ArchiveSelection(List.of(new ArchiveRange(1, 1)), false));
        Executable action = () -> executeArchiveCommand(archiveCommand, repository);
        HertaException exception = assertThrows(HertaException.class, action);

        assertTrue(exception.getMessage().startsWith("Failed to archive tasks: "));
        assertEquals(1, activeTasks.size());
        assertTrue(activeTasks.get(0).isCompleted());
        assertEquals("T | 1 | completed\n", Files.readString(activeFile));
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

    private void executeArchiveCommand(ArchiveCommand archiveCommand,
                                       TaskRepository repository) throws HertaException {
        archiveCommand.execute(repository, new Ui());
    }

    private void executeRestoreCommand(RestoreCommand restoreCommand,
                                       TaskRepository repository) throws HertaException {
        restoreCommand.execute(repository, new Ui());
    }

    @FunctionalInterface
    private interface OutputAction {
        void run() throws Exception;
    }
}
