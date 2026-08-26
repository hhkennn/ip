import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

/**
 * Parses and formats the date/time values used by deadline and event tasks.
 *
 * <p>User input may use either the slash format from the command example or
 * the ISO-like format from the minimal requirement. Stored values always use
 * Java's ISO local date/time format so that they can be parsed reliably when
 * Herta starts again.</p>
 */
public final class DateTimeParser {
    private static final List<DateTimeFormatter> USER_DATE_TIME_FORMATS = List.of(
            strictFormatter("d/M/uuuu HHmm"),
            strictFormatter("uuuu-MM-dd HHmm"),
            strictFormatter("uuuu-MM-dd HH:mm"));

    private static final DateTimeFormatter ISO_DATE_FORMAT =
            strictFormatter("uuuu-MM-dd");
    private static final List<DateTimeFormatter> FILTER_DATE_FORMATS = List.of(
            ISO_DATE_FORMAT,
            strictFormatter("d/M/uuuu"));
    private static final DateTimeFormatter STORAGE_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_OUTPUT_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME_OUTPUT_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd uuuu, h:mm a", Locale.ENGLISH);

    private DateTimeParser() {
        // Utility class; do not instantiate.
    }

    /**
     * Parses a deadline entered by the user.
     *
     * @param input the date/time entered after {@code /by}
     * @return the parsed date/time, with date-only input represented at midnight
     * @throws DateTimeParseException if the input does not match a supported format
     */
    public static LocalDateTime parseUserInput(String input) {
        String normalizedInput = input.trim();
        DateTimeParseException lastException = null;

        for (DateTimeFormatter formatter : USER_DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(normalizedInput, formatter);
            } catch (DateTimeParseException e) {
                lastException = e;
            }
        }

        try {
            LocalDate date = LocalDate.parse(normalizedInput, ISO_DATE_FORMAT);
            return date.atStartOfDay();
        } catch (DateTimeParseException e) {
            lastException = e;
        }

        throw lastException;
    }

    /**
     * Parses a date used by a date-filter command.
     *
     * @param input the date entered by the user
     * @return the parsed date
     * @throws DateTimeParseException if the input does not match a supported date format
     */
    public static LocalDate parseUserDate(String input) {
        String normalizedInput = input.trim();
        DateTimeParseException lastException = null;

        for (DateTimeFormatter formatter : FILTER_DATE_FORMATS) {
            try {
                return LocalDate.parse(normalizedInput, formatter);
            } catch (DateTimeParseException e) {
                lastException = e;
            }
        }

        throw lastException;
    }

    /**
     * Parses a date/time serialized in Herta's data file.
     *
     * @param input the serialized date/time
     * @return the parsed date/time
     * @throws DateTimeParseException if the stored value is invalid
     */
    public static LocalDateTime parseStoredValue(String input) {
        String normalizedInput = input.trim();
        try {
            return LocalDateTime.parse(normalizedInput, STORAGE_FORMAT);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(normalizedInput, ISO_DATE_FORMAT).atStartOfDay();
        }
    }

    /**
     * Formats a date/time for storage in Herta's data file.
     *
     * @param dateTime the date/time to serialize
     * @return the stable serialized representation
     */
    public static String formatForStorage(LocalDateTime dateTime) {
        return STORAGE_FORMAT.format(dateTime);
    }

    /**
     * Formats a date/time for display to the user.
     *
     * @param dateTime the date/time to display
     * @return a readable date or date/time representation
     */
    public static String formatForDisplay(LocalDateTime dateTime) {
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return DATE_OUTPUT_FORMAT.format(dateTime);
        }
        return DATE_TIME_OUTPUT_FORMAT.format(dateTime);
    }

    /**
     * Formats a calendar date for display to the user.
     *
     * @param date the date to display
     * @return a readable date representation
     */
    public static String formatDateForDisplay(LocalDate date) {
        return DATE_OUTPUT_FORMAT.format(date);
    }

    /**
     * Creates a strict formatter using a pattern with a proleptic year.
     *
     * @param pattern the date/time pattern
     * @return a strict formatter
     */
    private static DateTimeFormatter strictFormatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern)
                .withResolverStyle(ResolverStyle.STRICT);
    }
}
