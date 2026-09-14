package herta.command;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import herta.ui.UiOutput;

/**
 * Tests archive and restore command execution, persistence, and error handling.
 */
class ArchiveRestoreCommandTest {
    @TempDir
    Path temporaryDirectory;

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
                + "The active list is down to 0 tasks. Much tidier.",
                response.getMessage().replace(System.lineSeparator(), "\n"));
        assertEquals(List.of(), Files.readAllLines(activeFile));
        assertEquals(List.of("T | 1 | first", "T | 1 | second", "T | 1 | third"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void archiveCommand_overlappingSelectors_archiveEachTaskOnceInOrder() throws Exception {
        Path activeFile = temporaryDirectory.resolve("overlapping").resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo first");
        herta.getResponse("todo second");
        herta.getResponse("todo third");
        herta.getResponse("todo fourth");
        herta.getResponse("mark 1");
        herta.getResponse("mark 2");
        herta.getResponse("mark 3");
        herta.getResponse("mark 4");

        HertaResponse response = herta.getResponse("archive 1-3 2 3-4");

        assertEquals(ResponseCategory.ARCHIVE, response.getResponseCategory());
        assertTrue(response.getMessage().contains("archived 4 completed tasks"));
        assertEquals(List.of("T | 1 | first", "T | 1 | second", "T | 1 | third", "T | 1 | fourth"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
        assertEquals(List.of(), Files.readAllLines(activeFile));
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
        assertEquals("That task is still unfinished. Complete it before archiving.",
                response.getMessage());
        assertEquals(List.of("T | 1 | complete", "T | 0 | incomplete"),
                Files.readAllLines(activeFile));
        assertTrue(Files.notExists(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void duplicateTasks_areAllowedInEveryList() throws Exception {
        Path activeFile = temporaryDirectory.resolve("global-duplicate").resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());

        herta.getResponse("todo read book");
        HertaResponse duplicateActiveResponse = herta.getResponse("todo read book");
        herta.getResponse("mark 1");
        herta.getResponse("archive 1");
        herta.getResponse("mark 1");
        HertaResponse duplicateArchiveResponse = herta.getResponse("archive 1");
        HertaResponse firstRestoreResponse = herta.getResponse("restore 1");
        HertaResponse duplicateRestoreResponse = herta.getResponse("restore 1");

        assertEquals(ResponseCategory.ADD, duplicateActiveResponse.getResponseCategory());
        assertEquals(ResponseCategory.ARCHIVE, duplicateArchiveResponse.getResponseCategory());
        assertEquals(ResponseCategory.RESTORE, firstRestoreResponse.getResponseCategory());
        assertEquals(ResponseCategory.RESTORE, duplicateRestoreResponse.getResponseCategory());
        assertEquals(List.of("T | 1 | read book", "T | 1 | read book"),
                Files.readAllLines(activeFile));
        assertEquals(List.of(), Files.readAllLines(activeFile.resolveSibling("archive.txt")));
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
        assertEquals("Nothing is ready for archiving. Complete a task first.",
                noOpResponse.getMessage());
        assertEquals(List.of("T | 1 | complete"),
                Files.readAllLines(activeFile.resolveSibling("archive.txt")));
    }

    @Test
    void archiveAll_emptyActiveList_reportsNoTasks() throws Exception {
        Path activeFile = temporaryDirectory.resolve("empty-archive").resolve("tasks.txt");
        HertaResponse response = new Herta(activeFile.toString()).getResponse("archive all");

        assertEquals(ResponseCategory.ARCHIVE, response.getResponseCategory());
        assertEquals("No active tasks. There is nothing here to archive.", response.getMessage());
        assertTrue(Files.notExists(activeFile));
    }

    @Test
    void archiveAll_onlyCompletedTasks_movesAllTasksAndReportsPluralCount() throws Exception {
        Path activeFile = temporaryDirectory.resolve("all-completed").resolve("tasks.txt");
        Herta herta = new Herta(activeFile.toString());
        herta.getResponse("todo first");
        herta.getResponse("todo second");
        herta.getResponse("mark 1");
        herta.getResponse("mark 2");

        HertaResponse response = herta.getResponse("archive all");

        assertEquals(ResponseCategory.ARCHIVE, response.getResponseCategory());
        assertTrue(response.getMessage().contains("archived 2 completed tasks"));
        assertEquals(List.of(), Files.readAllLines(activeFile));
        assertEquals(List.of("T | 1 | first", "T | 1 | second"),
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
        assertEquals("The archive, as requested:\n"
                + "1.[T][X] completed\n"
                + "2.[T][ ] incomplete", viewResponse.getMessage()
                .replace(System.lineSeparator(), "\n"));
        assertEquals(ResponseCategory.RESTORE, restoreResponse.getResponseCategory());
        assertEquals("There. I've restored it:\n"
                + "  [T][ ] incomplete\n"
                + "That makes 1 active task. Back where it belongs.", restoreResponse.getMessage()
                .replace(System.lineSeparator(), "\n"));
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

        assertEquals("That number points to nothing on the active list. Check again.",
                response.getMessage());
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
    void restoreCommand_firstMiddleLast_preservesAppendOrderAndCounts() throws Exception {
        Path activeFile = temporaryDirectory.resolve("restore-order").resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        new Storage(archiveFile.toString()).save(new TaskList(List.of(
                new Todo("first"), new Todo("middle"), new Todo("last"))));
        Herta herta = new Herta(activeFile.toString());

        HertaResponse middleResponse = herta.getResponse("restore 2");
        HertaResponse firstResponse = herta.getResponse("restore 1");
        HertaResponse lastResponse = herta.getResponse("restore 1");

        assertTrue(middleResponse.getMessage().contains("That makes 1 active task."));
        assertTrue(firstResponse.getMessage().contains("That makes 2 active tasks."));
        assertTrue(lastResponse.getMessage().contains("That makes 3 active tasks."));
        assertEquals(List.of("T | 0 | middle", "T | 0 | first", "T | 0 | last"),
                Files.readAllLines(activeFile));
        assertEquals(List.of(), Files.readAllLines(archiveFile));
    }

    @Test
    void restoreCommand_invalidIndex_leavesArchiveUnchanged() throws Exception {
        Path activeFile = temporaryDirectory.resolve("restore-invalid").resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        new Storage(archiveFile.toString()).save(new TaskList(List.of(new Todo("archived"))));
        Herta herta = new Herta(activeFile.toString());

        HertaResponse response = herta.getResponse("restore 2");

        assertEquals(ResponseCategory.ERROR, response.getResponseCategory());
        assertEquals("That number points to nothing in the archive. Check again.", response.getMessage());
        assertEquals(List.of("T | 0 | archived"), Files.readAllLines(archiveFile));
    }

    @Test
    void restoreCommand_atCapacity_leavesBothCollectionsAndFilesUnchanged() throws Exception {
        Path activeFile = temporaryDirectory.resolve("restore-capacity").resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        Todo activeTask = new Todo("active");
        Todo archivedTask = new Todo("archived");
        TaskList activeTasks = new TaskList(Collections.nCopies(TaskList.MAXIMUM_TASK_COUNT, activeTask));
        TaskList archivedTasks = new TaskList(List.of(archivedTask));
        Storage activeStorage = new Storage(activeFile.toString());
        Storage archiveStorage = new Storage(archiveFile.toString());
        archiveStorage.save(archivedTasks);
        TaskRepository repository = new TaskRepository(activeStorage, archiveStorage,
                activeTasks, archivedTasks);

        assertThrows(IllegalArgumentException.class, () ->
                new RestoreCommand(0).execute(repository, new Ui()));

        assertEquals(TaskList.MAXIMUM_TASK_COUNT, activeTasks.size());
        assertEquals(1, archivedTasks.size());
        assertTrue(Files.notExists(activeFile));
        assertEquals(List.of("T | 0 | archived"), Files.readAllLines(archiveFile));
    }

    @Test
    void archiveCommand_atArchiveCapacity_preservesCollectionsAndFiles() throws Exception {
        Path activeFile = temporaryDirectory.resolve("archive-capacity").resolve("tasks.txt");
        Path archiveFile = activeFile.resolveSibling("archive.txt");
        Todo activeTask = new Todo("active");
        activeTask.markAsDone();
        Todo archivedTask = new Todo("archived");
        TaskList activeTasks = new TaskList(List.of(activeTask));
        TaskList archivedTasks = new TaskList(Collections.nCopies(
                TaskList.MAXIMUM_TASK_COUNT, archivedTask));
        Storage activeStorage = new Storage(activeFile.toString());
        Storage archiveStorage = new Storage(archiveFile.toString());
        activeStorage.save(activeTasks);
        archiveStorage.save(archivedTasks);
        byte[] originalActiveBytes = Files.readAllBytes(activeFile);
        byte[] originalArchiveBytes = Files.readAllBytes(archiveFile);
        TaskRepository repository = new TaskRepository(activeStorage, archiveStorage,
                activeTasks, archivedTasks);

        assertThrows(IllegalArgumentException.class, () ->
                new ArchiveCommand(new ArchiveSelection(List.of(new ArchiveRange(1, 1)), false))
                        .execute(repository, new Ui()));

        assertEquals(1, activeTasks.size());
        assertEquals(TaskList.MAXIMUM_TASK_COUNT, archivedTasks.size());
        assertArrayEquals(originalActiveBytes, Files.readAllBytes(activeFile));
        assertArrayEquals(originalArchiveBytes, Files.readAllBytes(archiveFile));
    }

    @Test
    void legacyCommands_useSiblingArchiveForArchiveViewAndRestore() throws Exception {
        Path activeFile = temporaryDirectory.resolve("legacy").resolve("tasks.txt");
        Storage activeStorage = new Storage(activeFile.toString());
        Todo task = new Todo("legacy task");
        task.markAsDone();
        TaskList activeTasks = new TaskList(List.of(task));
        activeStorage.save(activeTasks);
        RecordingOutput output = new RecordingOutput();

        new ArchiveCommand(new ArchiveSelection(List.of(new ArchiveRange(1, 1)), false))
                .execute(activeTasks, output, activeStorage);
        new ArchivedCommand().execute(activeTasks, output, activeStorage);
        new RestoreCommand(0).execute(activeTasks, output, activeStorage);

        assertEquals(1, activeTasks.size());
        assertEquals(0, new Storage(activeFile.resolveSibling("archive.txt").toString())
                .loadArchived().size());
        assertTrue(output.messages.contains("1.[T][X] legacy task"));
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

    private void executeArchiveCommand(ArchiveCommand archiveCommand,
                                       TaskRepository repository) throws HertaException {
        archiveCommand.execute(repository, new Ui());
    }

    private void executeRestoreCommand(RestoreCommand restoreCommand,
                                       TaskRepository repository) throws HertaException {
        restoreCommand.execute(repository, new Ui());
    }

    private static final class RecordingOutput implements UiOutput {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void showMessage(String message) {
            messages.add(message);
        }

        @Override
        public void showGoodbye() {
            messages.add(UiOutput.GOODBYE_MESSAGE);
        }
    }
}
