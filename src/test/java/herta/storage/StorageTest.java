package herta.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import herta.exception.HertaException;
import herta.task.Deadline;
import herta.task.Event;
import herta.task.TaskList;
import herta.task.Todo;

/**
 * Tests persistence of task data and validation of malformed storage records.
 */
class StorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void saveThenLoad_roundTripPreservesTasksAndStatuses() throws Exception {
        Path dataFile = temporaryDirectory.resolve("herta.txt");
        Todo todo = new Todo("buy milk");
        todo.markAsDone();
        Deadline deadline = new Deadline("submit report",
                LocalDateTime.of(2019, 12, 2, 18, 0));
        Event event = new Event("team meeting",
                LocalDateTime.of(2019, 12, 3, 10, 0),
                LocalDateTime.of(2019, 12, 3, 11, 0));
        TaskList originalTasks = new TaskList(List.of(todo, deadline, event));

        new Storage(dataFile.toString()).save(originalTasks);

        assertEquals(List.of(todo.toStorageString(), deadline.toStorageString(),
                event.toStorageString()), Files.readAllLines(dataFile));

        TaskList loadedTasks = new Storage(dataFile.toString()).load();
        assertEquals(3, loadedTasks.size());
        Todo loadedTodo = assertInstanceOf(Todo.class, loadedTasks.get(0));
        Deadline loadedDeadline = assertInstanceOf(Deadline.class, loadedTasks.get(1));
        Event loadedEvent = assertInstanceOf(Event.class, loadedTasks.get(2));
        assertTrue(loadedTodo.isCompleted());
        assertEquals(deadline.getBy(), loadedDeadline.getBy());
        assertEquals(event.getFrom(), loadedEvent.getFrom());
        assertEquals(event.getTo(), loadedEvent.getTo());
    }

    @Test
    void save_repeatedlyReplacesExistingFile() throws Exception {
        Path dataFile = temporaryDirectory.resolve("herta.txt");
        Storage storage = new Storage(dataFile.toString());

        storage.save(new TaskList(List.of(new Todo("first task"))));
        storage.save(new TaskList(List.of(new Todo("second task"))));

        assertEquals(List.of("T | 0 | second task"), Files.readAllLines(dataFile));
    }

    @Test
    void load_missingDataFile_returnsEmptyTaskList() throws HertaException {
        Path dataFile = temporaryDirectory.resolve("missing.txt");

        TaskList loadedTasks = new Storage(dataFile.toString()).load();

        assertEquals(0, loadedTasks.size());
    }

    @Test
    void load_directoryPath_throwsHelpfulException() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("data-directory");
        Files.createDirectory(dataDirectory);

        HertaException exception = assertThrows(HertaException.class, () ->
                new Storage(dataDirectory.toString()).load());

        assertEquals("Failed to load tasks: data path is not a regular file.",
                exception.getMessage());
    }

    @Test
    void load_malformedRecord_reportsLineNumber() throws Exception {
        Path dataFile = temporaryDirectory.resolve("malformed.txt");
        Files.writeString(dataFile, "T | 0 | valid task\nX | 0 | unknown task\n");

        HertaException exception = assertThrows(HertaException.class, () ->
                new Storage(dataFile.toString()).load());

        assertEquals("Failed to load tasks at line 2: Invalid saved task: unknown task type 'X'.",
                exception.getMessage());
    }

    @Test
    void save_taskWithStorageDelimiter_rejectsInvalidRecord() {
        Path dataFile = temporaryDirectory.resolve("invalid.txt");
        TaskList tasks = new TaskList(List.of(new Todo("contains | separator")));

        HertaException exception = assertThrows(HertaException.class, () ->
                new Storage(dataFile.toString()).save(tasks));

        assertEquals("Failed to save tasks: Invalid saved task: type T requires 3 fields.",
                exception.getMessage());
        assertTrue(Files.notExists(dataFile));
    }

    @Test
    void save_nullOrNullContainingTaskList_rejectsInvalidInput() {
        Storage storage = new Storage(temporaryDirectory.resolve("invalid.txt").toString());

        HertaException nullListException = assertThrows(HertaException.class, () ->
                storage.save(null));
        HertaException nullTaskException = assertThrows(HertaException.class, () ->
                storage.save(new TaskList(Collections.singletonList(null))));

        assertEquals("Failed to save tasks: task list is null.",
                nullListException.getMessage());
        assertEquals("Failed to save tasks: task list contains a null task.",
                nullTaskException.getMessage());
    }

    @Test
    void loadArchived_missingAndEmptyFiles_returnEmptyArchive() throws Exception {
        Path archiveFile = temporaryDirectory.resolve("archive.txt");
        Storage storage = new Storage(archiveFile.toString());

        assertEquals(0, storage.loadArchived().size());
        storage.save(new TaskList());
        assertTrue(Files.exists(archiveFile));
        assertEquals(0, storage.loadArchived().size());
    }

    @Test
    void loadArchived_preservesIncompleteRecordsAndUsesArchiveErrors() throws Exception {
        Path archiveFile = temporaryDirectory.resolve("archive.txt");
        Files.writeString(archiveFile, "T | 0 | incomplete\n");

        TaskList archivedTasks = new Storage(archiveFile.toString()).loadArchived();

        assertFalse(archivedTasks.get(0).isCompleted());
        Files.writeString(archiveFile, "T | 2 | invalid\n");
        Storage storage = new Storage(archiveFile.toString());
        HertaException exception = assertThrows(HertaException.class, storage::loadArchived);
        assertTrue(exception.getMessage().startsWith("Failed to load archived tasks: "));
    }

    @Test
    void loadArchived_invalidEncoding_reportsArchiveStartupFailure() throws Exception {
        Path archiveFile = temporaryDirectory.resolve("archive.txt");
        Files.write(archiveFile, new byte[] {(byte) 0xc3, (byte) 0x28});

        Storage storage = new Storage(archiveFile.toString());
        HertaException exception = assertThrows(HertaException.class, storage::loadArchived);

        assertTrue(exception.getMessage().startsWith("Failed to load archived tasks: "));
    }

    @Test
    void saveBoth_failureRestoresOriginalAbsence() throws Exception {
        Path activeFile = temporaryDirectory.resolve("tasks.txt");
        Path archiveDirectory = temporaryDirectory.resolve("archive.txt");
        Files.createDirectory(archiveDirectory);
        Storage activeStorage = new Storage(activeFile.toString());
        Storage archiveStorage = new Storage(archiveDirectory.toString());

        Executable action = () -> saveBothForTest(activeStorage, archiveStorage);
        HertaException exception = assertThrows(HertaException.class, action);

        assertTrue(exception.getMessage().startsWith("Failed to archive tasks: "));
        assertTrue(Files.notExists(activeFile));
        assertTrue(Files.isDirectory(archiveDirectory));
    }

    @Test
    void validateDistinctPaths_rejectsNormalizedConflicts() {
        Path activeFile = temporaryDirectory.resolve("data").resolve("herta.txt");
        Path conflictingArchive = temporaryDirectory.resolve("data").resolve(".").resolve("herta.txt");

        Executable action = () -> validatePathsForTest(activeFile, conflictingArchive);
        HertaException exception = assertThrows(HertaException.class, action);

        assertTrue(exception.getMessage().startsWith("Failed to load archived tasks: "));
    }

    private void saveBothForTest(Storage activeStorage, Storage archiveStorage)
            throws HertaException {
        activeStorage.saveBoth(archiveStorage, new TaskList(), new TaskList(),
                "Failed to archive tasks: ");
    }

    private void validatePathsForTest(Path activeFile, Path archiveFile) throws HertaException {
        Storage.validateDistinctPaths(activeFile, archiveFile);
    }
}
