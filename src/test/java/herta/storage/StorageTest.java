package herta.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
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
        assertTrue(loadedTodo.isDone());
        assertEquals(deadline.getBy(), loadedDeadline.getBy());
        assertEquals(event.getFrom(), loadedEvent.getFrom());
        assertEquals(event.getTo(), loadedEvent.getTo());
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
                storage.save(new TaskList(java.util.Arrays.asList((Todo) null))));

        assertEquals("Failed to save tasks: task list is null.",
                nullListException.getMessage());
        assertEquals("Failed to save tasks: task list contains a null task.",
                nullTaskException.getMessage());
    }
}
