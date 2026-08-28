package herta.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Tests the common completion, scheduling, and formatting behavior of a base task.
 */
class TaskTest {

    @Test
    void taskStatusAndFormatting_changeAfterCompletion() {
        Task task = new Task("plain task");

        assertEquals("plain task", task.getDescription());
        assertEquals(" ", task.getStatusIcon());
        assertFalse(task.isDone());
        assertFalse(task.occursOn(LocalDate.of(2019, 10, 15)));
        assertTrue(task.getScheduledDateTime().isEmpty());
        assertFalse(task.isUpcoming(LocalDateTime.MIN, LocalDateTime.MAX));
        assertEquals("T | 0 | plain task", task.toStorageString());
        assertEquals("[ ] plain task", task.toString());

        task.markAsDone();

        assertTrue(task.isDone());
        assertEquals("X", task.getStatusIcon());
        assertEquals("T | 1 | plain task", task.toStorageString());
        assertEquals("[X] plain task", task.toString());

        task.markAsNotDone();
        assertFalse(task.isDone());
    }
}
