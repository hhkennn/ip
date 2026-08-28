package herta.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests task-list state changes and the index-based query operations used by commands.
 */
class TaskListTest {

    @Test
    void markUnmarkAndRestoreStatus_updateTaskAndReportPreviousStatus() {
        TaskList tasks = new TaskList(List.of(new Todo("read book")));

        assertFalse(tasks.markTask(0));
        assertTrue(tasks.get(0).isDone());
        assertTrue(tasks.markTask(0));

        assertTrue(tasks.unmarkTask(0));
        assertFalse(tasks.get(0).isDone());
        assertFalse(tasks.unmarkTask(0));

        tasks.restoreStatus(0, true);
        assertTrue(tasks.get(0).isDone());
        tasks.restoreStatus(0, false);
        assertFalse(tasks.get(0).isDone());
    }

    @Test
    void matchingIndices_returnsOnlyMatchingTasksInStoredOrder() {
        TaskList tasks = new TaskList(List.of(
                new Todo("buy milk"),
                new Deadline("submit report", LocalDateTime.of(2019, 10, 15, 18, 0)),
                new Event("project meeting",
                        LocalDateTime.of(2019, 10, 14, 23, 0),
                        LocalDateTime.of(2019, 10, 16, 1, 0))));

        assertEquals(List.of(1, 2),
                tasks.matchingIndices(task -> task.occursOn(LocalDate.of(2019, 10, 15))));
    }

    @Test
    void sortedIndices_returnsSortedIndicesWithoutChangingStoredOrder() {
        Todo todo = new Todo("buy milk");
        Deadline later = new Deadline("later", LocalDateTime.of(2019, 10, 16, 18, 0));
        Deadline earlier = new Deadline("earlier", LocalDateTime.of(2019, 10, 15, 18, 0));
        TaskList tasks = new TaskList(List.of(todo, later, earlier));

        List<Integer> sortedIndices = tasks.sortedIndices(Comparator.comparing(
                task -> task.getScheduledDateTime().orElse(LocalDateTime.MAX)));

        assertEquals(List.of(2, 1, 0), sortedIndices);
        assertEquals(todo, tasks.get(0));
        assertEquals(later, tasks.get(1));
        assertEquals(earlier, tasks.get(2));
    }

    @Test
    void asUnmodifiableList_rejectsStructuralChanges() {
        TaskList tasks = new TaskList(List.of(new Todo("read book")));

        assertThrows(UnsupportedOperationException.class, () ->
                tasks.asUnmodifiableList().add(new Todo("write book")));
    }
}
