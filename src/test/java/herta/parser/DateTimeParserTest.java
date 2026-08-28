package herta.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

/**
 * Tests the date/time input parsing used by deadline and event commands.
 */
class DateTimeParserTest {

    @Test
    void parseUserInput_supportedDateTimeFormats_returnsExpectedDateTime() {
        LocalDateTime expected = LocalDateTime.of(2019, 12, 2, 18, 0);

        assertEquals(expected, DateTimeParser.parseUserInput("2/12/2019 1800"));
        assertEquals(expected, DateTimeParser.parseUserInput("2019-12-02 1800"));
        assertEquals(expected, DateTimeParser.parseUserInput("2019-12-02 18:00"));
    }

    @Test
    void parseUserInput_dateOnlyInput_returnsStartOfDay() {
        assertEquals(LocalDateTime.of(2019, 12, 2, 0, 0),
                DateTimeParser.parseUserInput("2019-12-02"));
    }

    @Test
    void parseUserInput_surroundingWhitespace_returnsExpectedDateTime() {
        assertEquals(LocalDateTime.of(2019, 12, 2, 18, 0),
                DateTimeParser.parseUserInput("  2/12/2019 1800  "));
    }

    @Test
    void parseUserInput_invalidInput_throwsDateTimeParseException() {
        String[] invalidInputs = {
            "31/02/2019 1800",
            "2019-10-15 2460",
            "2019-10-15 18:60",
            "not a date",
            ""
        };

        for (String input : invalidInputs) {
            assertThrows(DateTimeParseException.class, () ->
                    DateTimeParser.parseUserInput(input));
        }
    }

    @Test
    void parseUserDate_supportedDateFormats_returnsExpectedDate() {
        LocalDate expected = LocalDate.of(2019, 12, 2);

        assertEquals(expected, DateTimeParser.parseUserDate("2019-12-02"));
        assertEquals(expected, DateTimeParser.parseUserDate("2/12/2019"));
    }

    @Test
    void parseUserDate_surroundingWhitespace_returnsExpectedDate() {
        assertEquals(LocalDate.of(2019, 12, 2),
                DateTimeParser.parseUserDate("  2/12/2019  "));
    }

    @Test
    void parseUserDate_invalidInput_throwsDateTimeParseException() {
        String[] invalidInputs = {"31/02/2019", "2019-02-30", "2019-12-02 1800", "not a date"};

        for (String input : invalidInputs) {
            assertThrows(DateTimeParseException.class, () ->
                    DateTimeParser.parseUserDate(input));
        }
    }

    @Test
    void parseStoredValue_datetimeInput_returnsExpectedDateTime() {
        LocalDateTime expected = LocalDateTime.of(2019, 12, 2, 18, 0);

        assertEquals(expected, DateTimeParser.parseStoredValue("2019-12-02T18:00:00"));
    }

    @Test
    void parseStoredValue_dateOnlyInput_returnsStartOfDay() {
        assertEquals(LocalDateTime.of(2019, 12, 2, 0, 0),
                DateTimeParser.parseStoredValue("2019-12-02"));
    }

    @Test
    void parseStoredValue_surroundingWhitespace_returnsExpectedDateTime() {
        assertEquals(LocalDateTime.of(2019, 12, 2, 18, 0),
                DateTimeParser.parseStoredValue("  2019-12-02T18:00:00  "));
    }

    @Test
    void parseStoredValue_invalidInput_throwsDateTimeParseException() {
        assertThrows(DateTimeParseException.class, () ->
                DateTimeParser.parseStoredValue("2019-02-30T18:00:00"));
    }

    @Test
    void formatForStorage_dateTime_returnsIsoLocalDateTime() {
        LocalDateTime dateTime = LocalDateTime.of(2019, 12, 2, 18, 0, 5);

        assertEquals("2019-12-02T18:00:05", DateTimeParser.formatForStorage(dateTime));
    }

    @Test
    void formatForDisplay_midnight_returnsDateOnlyFormat() {
        assertEquals("Dec 02 2019",
                DateTimeParser.formatForDisplay(LocalDateTime.of(2019, 12, 2, 0, 0)));
    }

    @Test
    void formatForDisplay_nonMidnight_returnsDateTimeFormat() {
        assertEquals("Dec 02 2019, 6:05 PM",
                DateTimeParser.formatForDisplay(LocalDateTime.of(2019, 12, 2, 18, 5)));
    }

    @Test
    void formatDateForDisplay_date_returnsReadableDate() {
        assertEquals("Dec 02 2019",
                DateTimeParser.formatDateForDisplay(LocalDate.of(2019, 12, 2)));
    }
}
