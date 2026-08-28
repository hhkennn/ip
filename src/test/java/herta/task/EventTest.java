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
    void fromStorage_validValues_reconstructEvent() {
        Event event = Event.fromStorage("team meeting", "2019-12-03T10:00:00",
                "2019-12-03T11:00:00");

        assertEquals("team meeting", event.getDescription());
        assertEquals(LocalDateTime.of(2019, 12, 3, 10, 0), event.getFrom());
        assertEquals(LocalDateTime.of(2019, 12, 3, 11, 0), event.getTo());
    }

    @Test
    void fromStorage_invalidRange_throwsHelpfulException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                Event.fromStorage("team meeting", "2019-12-03T11:00:00",
                        "2019-12-03T10:00:00"));

        assertEquals("Invalid saved event: end must be after start.", exception.getMessage());
    }

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

    @Test
    void eventFormatting_includesStatusDescriptionAndRange() {
        Event event = new Event("team meeting",
                LocalDateTime.of(2019, 12, 3, 10, 0),
                LocalDateTime.of(2019, 12, 3, 11, 0));

        assertEquals(LocalDateTime.of(2019, 12, 3, 10, 0),
                event.getScheduledDateTime().orElseThrow());
        assertEquals("E | 0 | team meeting | 2019-12-03T10:00:00 | 2019-12-03T11:00:00",
                event.toStorageString());
        assertEquals("[E][ ] team meeting (from: Dec 03 2019, 10:00 AM to: "
                + "Dec 03 2019, 11:00 AM)", event.toString());
    }
}
