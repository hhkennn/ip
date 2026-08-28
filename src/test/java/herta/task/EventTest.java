package herta.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Tests event range validation and calendar-date overlap behavior.
 */
class EventTest {

    @Test
    void constructor_nonIncreasingRange_throwsIllegalArgumentException() {
        LocalDateTime start = LocalDateTime.of(2019, 10, 15, 10, 0);

        IllegalArgumentException sameTime = assertThrows(IllegalArgumentException.class, () ->
                new Event("meeting", start, start));
        IllegalArgumentException earlierEnd = assertThrows(IllegalArgumentException.class, () ->
                new Event("meeting", start, start.minusMinutes(1)));

        assertEquals("Event end must be after its start.", sameTime.getMessage());
        assertEquals("Event end must be after its start.", earlierEnd.getMessage());
    }

    @Test
    void occursOn_eventSpanningMidnight_matchesBothCalendarDates() {
        Event event = new Event("overnight event",
                LocalDateTime.of(2019, 10, 14, 23, 0),
                LocalDateTime.of(2019, 10, 15, 1, 0));

        assertTrue(event.occursOn(LocalDate.of(2019, 10, 14)));
        assertTrue(event.occursOn(LocalDate.of(2019, 10, 15)));
        assertFalse(event.occursOn(LocalDate.of(2019, 10, 16)));
    }

    @Test
    void isUpcoming_startInclusiveAndEndExclusive() {
        LocalDateTime start = LocalDateTime.of(2019, 10, 15, 10, 0);
        Event event = new Event("meeting", start, start.plusHours(1));

        assertTrue(event.isUpcoming(start, start.plusDays(1)));
        assertFalse(event.isUpcoming(start.plusHours(1), start.plusDays(1)));
    }
}
