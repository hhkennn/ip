package herta.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import herta.exception.HertaException;
import herta.storage.Storage;
import herta.storage.TaskRepository;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.Task;
import herta.task.TaskList;
import herta.task.Todo;
import herta.ui.Ui;

/**
 * Tests command execution, persistence coordination, task queries, and command errors.
 */
class CommandTest {
    private static final int BROAD_UPCOMING_WINDOW_DAYS = 100_000_000;
    private static final LocalDateTime FAR_FUTURE_DATE_TIME =
            LocalDateTime.of(9999, 12, 31, 23, 59);

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
        assertTrue(output.contains("That makes 1 active task."));
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
        assertTrue(markOutput.contains("Done. It's marked complete:"));

        String unmarkOutput = captureOutput(() ->
                new UnmarkCommand(0).execute(tasks, new Ui(), storage));
        assertFalse(todo.isCompleted());
        assertTrue(unmarkOutput.contains("Fine. It's incomplete again:"));

        String deleteOutput = captureOutput(() ->
                new DeleteCommand(0).execute(tasks, new Ui(), storage));
        assertEquals(0, tasks.size());
        assertTrue(deleteOutput.contains("Gone. I've removed it:"));
        assertEquals(List.of(), Files.readAllLines(dataFile));
    }

    @Test
    void taskChangingCommands_saveFailureRestoresOrPreservesMemory() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("not-a-file");
        Files.createDirectory(dataDirectory);
        Storage failingStorage = new Storage(dataDirectory.toString());

        Todo todo = new Todo("read book");
        TaskList tasks = new TaskList(List.of(todo));

        assertMarkFailureRestoresStatus(failingStorage, tasks, todo);
        assertUnmarkFailureRestoresStatus(failingStorage, tasks, todo);
        assertDeleteFailurePreservesTask(failingStorage, tasks, todo);
        assertAddFailurePreservesTaskList(failingStorage);
        assertInvalidIndexReportsHelpfulMessage(failingStorage, tasks);
    }

    private void assertMarkFailureRestoresStatus(Storage failingStorage, TaskList tasks, Todo todo) {
        assertThrows(HertaException.class, () ->
                new MarkCommand(0).execute(tasks, new Ui(), failingStorage));
        assertFalse(todo.isCompleted());
    }

    private void assertUnmarkFailureRestoresStatus(Storage failingStorage, TaskList tasks, Todo todo) {
        todo.markAsDone();
        assertThrows(HertaException.class, () ->
                new UnmarkCommand(0).execute(tasks, new Ui(), failingStorage));
        assertTrue(todo.isCompleted());
    }

    private void assertDeleteFailurePreservesTask(Storage failingStorage, TaskList tasks, Todo todo) {
        assertThrows(HertaException.class, () ->
                new DeleteCommand(0).execute(tasks, new Ui(), failingStorage));
        assertEquals(1, tasks.size());
        assertSame(todo, tasks.get(0));
    }

    private void assertAddFailurePreservesTaskList(Storage failingStorage) {
        TaskList invalidAddTasks = new TaskList();
        assertThrows(HertaException.class, () ->
                new TodoCommand(new Todo("valid task"))
                        .execute(invalidAddTasks, new Ui(), failingStorage));
        assertEquals(0, invalidAddTasks.size());
    }

    private void assertInvalidIndexReportsHelpfulMessage(Storage failingStorage, TaskList tasks) {
        HertaException invalidIndexException = assertThrows(HertaException.class, () ->
                new MarkCommand(1).execute(tasks, new Ui(), failingStorage));
        assertEquals("No active task has that number. Check the list and try again.",
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

        assertListCommandDisplaysTasks(tasks);
        assertFilterCommandDisplaysMatchingTasks(tasks);
        assertFindCommandDisplaysMatchingTasks(tasks);
        assertSortCommandDisplaysTasksInDateOrder(tasks, todo);
    }

    private void assertListCommandDisplaysTasks(TaskList tasks) throws Exception {
        String output = captureOutput(() -> new ListCommand().execute(tasks, new Ui(), null));
        assertTrue(output.contains("1.[T][ ] buy milk"));
        assertTrue(output.contains("2.[D][ ] submit report"));
        assertTrue(output.contains("3.[E][ ] project meeting"));
    }

    private void assertFilterCommandDisplaysMatchingTasks(TaskList tasks) throws Exception {
        String output = captureOutput(() -> new FilterCommand(LocalDate.of(2019, 10, 15))
                .execute(tasks, new Ui(), null));
        assertTrue(output.contains("Here's what's scheduled for Oct 15 2019. Try not to miss it:"));
        assertTrue(output.contains("2.[D][ ] submit report"));
        assertTrue(output.contains("3.[E][ ] project meeting"));
        assertFalse(output.contains("1.[T][ ] buy milk"));
    }

    private void assertFindCommandDisplaysMatchingTasks(TaskList tasks) throws Exception {
        String output = captureOutput(() -> new FindCommand("REPORT").execute(tasks, new Ui(), null));
        assertTrue(output.contains("Found them. Here are the matches:"));
        assertTrue(output.contains("2.[D][ ] submit report"));
        assertFalse(output.contains("1.[T][ ] buy milk"));
        assertFalse(output.contains("3.[E][ ] project meeting"));
    }

    private void assertSortCommandDisplaysTasksInDateOrder(TaskList tasks, Todo todo) throws Exception {
        String output = captureOutput(() -> new SortCommand().execute(tasks, new Ui(), null));
        assertTrue(output.contains("There. Your tasks are in date order."));
        assertTrue(output.indexOf("3.[E][ ] project meeting")
                < output.indexOf("2.[D][ ] submit report"));
        assertTrue(output.indexOf("2.[D][ ] submit report")
                < output.indexOf("1.[T][ ] buy milk"));
        assertSame(todo, tasks.get(0));
    }

    @Test
    void filterCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        TaskList tasks = new TaskList(List.of(new Todo("buy milk")));

        String output = captureOutput(() ->
                new FilterCommand(LocalDate.of(2019, 10, 15))
                        .execute(tasks, new Ui(), null));

        assertTrue(output.contains("No tasks on Oct 15 2019. A remarkably empty date."));
    }

    @Test
    void findCommand_noMatchesDisplaysEmptyMessage() throws Exception {
        String output = captureOutput(() ->
                new FindCommand("missing")
                        .execute(new TaskList(List.of(new Todo("buy milk"))), new Ui(), null));

        assertTrue(output.contains(
                "Nothing matched. Try a more useful keyword."));
        assertFalse(output.contains(
                "Found them. Here are the matches:"));
    }

    @Test
    void upcomingCommand_executeShowsOnlyIncompleteFutureTasks() throws Exception {
        Deadline upcoming = new Deadline("upcoming report", FAR_FUTURE_DATE_TIME);
        Deadline completed = new Deadline("completed report", FAR_FUTURE_DATE_TIME);
        completed.markAsDone();
        TaskList tasks = new TaskList(List.of(new Todo("buy milk"), upcoming, completed));

        String output = captureOutput(() ->
                new UpcomingCommand(BROAD_UPCOMING_WINDOW_DAYS).execute(tasks, new Ui(), null));

        assertTrue(output.contains("The next " + BROAD_UPCOMING_WINDOW_DAYS
                + " days, arranged for you:"));
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

        assertEquals("Nothing? Use a command. Try: list, find <keyword>, or todo <description>.",
                emptyException.getMessage());
        assertTrue(invalidException.getMessage().startsWith(
                "That command isn't in my vocabulary."));
    }

    @Test
    void constructors_invalidArguments_rejectCommands() {
        assertThrows(NullPointerException.class, () -> new TodoCommand(null));
        assertThrows(NullPointerException.class, () -> new DeadlineCommand(null));
        assertThrows(NullPointerException.class, () -> new EventCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new FindCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new FindCommand("   "));
        assertThrows(IllegalArgumentException.class, () -> new UpcomingCommand(0));
        assertThrows(IllegalArgumentException.class, () -> new MarkCommand(-1));
        assertThrows(IllegalArgumentException.class, () -> new UnmarkCommand(-1));
        assertThrows(IllegalArgumentException.class, () -> new DeleteCommand(-1));
        assertThrows(NullPointerException.class, () -> new ArchiveCommand(null));
        assertThrows(IllegalArgumentException.class, () -> new RestoreCommand(-1));
    }

    @Test
    void queries_emptyLists_reportExpectedMessages() throws Exception {
        TaskList emptyTasks = new TaskList();

        String listOutput = captureOutput(() -> new ListCommand().execute(emptyTasks, new Ui(), null));
        String findOutput = captureOutput(() -> new FindCommand("missing")
                .execute(emptyTasks, new Ui(), null));
        String filterOutput = captureOutput(() -> new FilterCommand(LocalDate.of(2019, 10, 15))
                .execute(emptyTasks, new Ui(), null));
        String sortOutput = captureOutput(() -> new SortCommand().execute(emptyTasks, new Ui(), null));
        String upcomingOutput = captureOutput(() -> new UpcomingCommand(2)
                .execute(emptyTasks, new Ui(), null));

        assertTrue(listOutput.contains("Let's see what you've managed to pile up:"));
        assertTrue(findOutput.contains("Nothing matched."));
        assertTrue(filterOutput.contains("No tasks on Oct 15 2019."));
        assertTrue(sortOutput.contains("There. Your tasks are in date order."));
        assertTrue(upcomingOutput.contains("Nothing upcoming."));
    }

    @Test
    void queries_unicodeDuplicatesAndTies_preserveExpectedOrder() throws Exception {
        Todo firstDuplicate = new Todo("Überraschung");
        Todo secondDuplicate = new Todo("Überraschung");
        Deadline firstDeadline = new Deadline("first due", LocalDateTime.of(2019, 10, 15, 18, 0));
        Deadline secondDeadline = new Deadline("second due", LocalDateTime.of(2019, 10, 15, 18, 0));
        TaskList tasks = new TaskList(List.of(firstDuplicate, secondDuplicate,
                firstDeadline, secondDeadline));

        String findOutput = captureOutput(() -> new FindCommand("ÜBER")
                .execute(tasks, new Ui(), null));
        String sortOutput = captureOutput(() -> new SortCommand().execute(tasks, new Ui(), null));

        assertTrue(findOutput.contains("1.[T][ ] Überraschung"));
        assertTrue(findOutput.contains("2.[T][ ] Überraschung"));
        assertTrue(sortOutput.indexOf("3.[D][ ] first due")
                < sortOutput.indexOf("4.[D][ ] second due"));
        assertSame(firstDuplicate, tasks.get(0));
        assertSame(firstDeadline, tasks.get(2));
    }

    @Test
    void taskStatusCommands_repeatedExecution_isIdempotent() throws Exception {
        Path dataFile = temporaryDirectory.resolve("status.txt");
        Todo todo = new Todo("repeat status");
        TaskList tasks = new TaskList(List.of(todo));
        Storage storage = new Storage(dataFile.toString());

        new MarkCommand(0).execute(tasks, new Ui(), storage);
        new MarkCommand(0).execute(tasks, new Ui(), storage);
        new UnmarkCommand(0).execute(tasks, new Ui(), storage);
        new UnmarkCommand(0).execute(tasks, new Ui(), storage);

        assertFalse(todo.isCompleted());
        assertEquals(List.of("T | 0 | repeat status"), Files.readAllLines(dataFile));
    }

    @Test
    void taskIndexCommands_invalidIndex_rejectEverySelectedTaskCommand() throws Exception {
        Path dataFile = temporaryDirectory.resolve("invalid-index.txt");
        TaskList tasks = new TaskList(List.of(new Todo("one task")));
        Storage storage = new Storage(dataFile.toString());
        storage.save(tasks);

        for (Command command : List.of(new MarkCommand(1), new UnmarkCommand(1),
                new DeleteCommand(1))) {
            assertThrows(HertaException.class, () -> command.execute(tasks, new Ui(), storage));
        }
        assertEquals(1, tasks.size());
    }

    @Test
    void repositoryAdapter_addChangesActiveFileOnly() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        TaskList activeTasks = new TaskList();
        Todo archivedTask = new Todo("archived task");
        TaskRepository repository = createRepositoryWithArchive(activeFile, archiveFile,
                activeTasks, archivedTask);

        new TodoCommand(new Todo("active task")).execute(repository, new Ui());

        assertEquals(List.of("T | 0 | active task"), Files.readAllLines(activeFile));
        assertEquals(List.of("T | 0 | archived task"), Files.readAllLines(archiveFile));
    }

    @Test
    void repositoryAdapter_markChangesActiveFileOnlyAndReportsInOrder() throws Exception {
        Path activeFile = temporaryDirectory.resolve("mark-repository.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        Todo activeTask = new Todo("active task");
        Todo archivedTask = new Todo("archived task");
        TaskRepository repository = createRepositoryWithArchive(activeFile, archiveFile,
                new TaskList(List.of(activeTask)), archivedTask);
        byte[] originalArchiveBytes = Files.readAllBytes(archiveFile);
        RecordingUiOutput output = new RecordingUiOutput();

        new MarkCommand(0).execute(repository, output);

        assertTrue(activeTask.isCompleted());
        assertEquals(List.of("Done. It's marked complete:", "  [T][X] active task"),
                output.getMessages());
        assertEquals(List.of("T | 1 | active task"), Files.readAllLines(activeFile));
        assertArrayEquals(originalArchiveBytes, Files.readAllBytes(archiveFile));
    }

    @Test
    void repositoryAdapter_unmarkChangesActiveFileOnlyAndReportsInOrder() throws Exception {
        Path activeFile = temporaryDirectory.resolve("unmark-repository.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        Todo activeTask = new Todo("active task");
        activeTask.markAsDone();
        Todo archivedTask = new Todo("archived task");
        TaskRepository repository = createRepositoryWithArchive(activeFile, archiveFile,
                new TaskList(List.of(activeTask)), archivedTask);
        byte[] originalArchiveBytes = Files.readAllBytes(archiveFile);
        RecordingUiOutput output = new RecordingUiOutput();

        new UnmarkCommand(0).execute(repository, output);

        assertFalse(activeTask.isCompleted());
        assertEquals(List.of("Fine. It's incomplete again:", "  [T][ ] active task"),
                output.getMessages());
        assertEquals(List.of("T | 0 | active task"), Files.readAllLines(activeFile));
        assertArrayEquals(originalArchiveBytes, Files.readAllBytes(archiveFile));
    }

    @Test
    void repositoryAdapter_deleteChangesActiveFileOnlyAndReportsInOrder() throws Exception {
        Path activeFile = temporaryDirectory.resolve("delete-repository.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        Todo activeTask = new Todo("active task");
        Todo archivedTask = new Todo("archived task");
        TaskRepository repository = createRepositoryWithArchive(activeFile, archiveFile,
                new TaskList(List.of(activeTask)), archivedTask);
        byte[] originalArchiveBytes = Files.readAllBytes(archiveFile);
        RecordingUiOutput output = new RecordingUiOutput();

        new DeleteCommand(0).execute(repository, output);

        assertEquals(0, repository.getActiveTasks().size());
        assertEquals(List.of("Gone. I've removed it:", "  [T][ ] active task",
                "That makes 0 active tasks. Try to keep up."), output.getMessages());
        assertEquals(List.of(), Files.readAllLines(activeFile));
        assertArrayEquals(originalArchiveBytes, Files.readAllBytes(archiveFile));
    }

    @Test
    void statusCommands_saveFailureRestoresStatusAndSuppressesSuccessOutput() {
        Storage failingStorage = new SaveFailingStorage(
                temporaryDirectory.resolve("status-failure.txt"));
        Todo incompleteTask = new Todo("incomplete task");
        TaskList incompleteTasks = new TaskList(List.of(incompleteTask));
        RecordingUiOutput markOutput = new RecordingUiOutput();

        assertThrows(IllegalStateException.class, () ->
                new MarkCommand(0).execute(incompleteTasks, markOutput, failingStorage));
        assertFalse(incompleteTask.isCompleted());
        assertEquals(List.of(), markOutput.getMessages());

        Todo completedTask = new Todo("completed task");
        completedTask.markAsDone();
        TaskList completedTasks = new TaskList(List.of(completedTask));
        RecordingUiOutput unmarkOutput = new RecordingUiOutput();

        assertThrows(IllegalStateException.class, () ->
                new UnmarkCommand(0).execute(completedTasks, unmarkOutput, failingStorage));
        assertTrue(completedTask.isCompleted());
        assertEquals(List.of(), unmarkOutput.getMessages());
    }

    private TaskRepository createRepositoryWithArchive(Path activeFile, Path archiveFile,
                                                       TaskList activeTasks, Task archivedTask)
            throws HertaException {
        Storage archiveStorage = new Storage(archiveFile.toString());
        TaskList archivedTasks = new TaskList(List.of(archivedTask));
        archiveStorage.save(archivedTasks);
        return new TaskRepository(new Storage(activeFile.toString()), archiveStorage,
                activeTasks, archivedTasks);
    }

    private String captureOutput(OutputAction action) throws Exception {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            action.run();
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOutput);
        }
    }

    @FunctionalInterface
    private interface OutputAction {
        void run() throws Exception;
    }

    private static final class SaveFailingStorage extends Storage {
        SaveFailingStorage(Path dataFile) {
            super(dataFile.toString());
        }

        @Override
        public void save(TaskList tasks) {
            throw new IllegalStateException("test save failure");
        }
    }
}
