package herta.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Tests deadline reconstruction, date queries, scheduling, and formatting.
 */
class DeadlineTest {

    @Test
    void fromStorage_validDateTime_reconstructsDeadline() {
        Deadline deadline = Deadline.fromStorage("submit report", "2019-12-02T18:00:00");

        assertEquals("submit report", deadline.getDescription());
        assertEquals(LocalDateTime.of(2019, 12, 2, 18, 0), deadline.getBy());
    }

    @Test
    void fromStorage_invalidDateTime_throwsHelpfulException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                Deadline.fromStorage("submit report", "2019-02-30"));

        assertEquals("Invalid saved deadline date/time: 2019-02-30.", exception.getMessage());
    }

    @Test
    void deadlineQueries_matchDueDateAndTimeBoundaries() {
        LocalDateTime due = LocalDateTime.of(2019, 10, 15, 18, 0);
        Deadline deadline = new Deadline("submit report", due);

        assertTrue(deadline.occursOn(LocalDate.of(2019, 10, 15)));
        assertFalse(deadline.occursOn(LocalDate.of(2019, 10, 16)));
        assertEquals(due, deadline.getScheduledDateTime().orElseThrow());
        assertTrue(deadline.isUpcoming(due, due.plusDays(1)));
        assertFalse(deadline.isUpcoming(due.plusDays(1), due.plusDays(2)));
    }

    @Test
    void deadlineFormatting_includesStatusDescriptionAndDate() {
        Deadline deadline = new Deadline("submit report",
                LocalDateTime.of(2019, 12, 2, 18, 0));

        assertEquals("D | 0 | submit report | 2019-12-02T18:00:00",
                deadline.toStorageString());
        assertEquals("[D][ ] submit report (by: Dec 02 2019, 6:00 PM)",
                deadline.toString());
    }
}
