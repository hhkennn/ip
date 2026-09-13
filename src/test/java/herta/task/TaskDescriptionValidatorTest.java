package herta.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Tests the shared task-description validation policy. */
class TaskDescriptionValidatorTest {

    @Test
    void validate_supportedUnicodeText_acceptsIt() {
        TaskDescriptionValidator.validate("review café ☕");
    }

    @Test
    void validate_delimiterControlsInvisibleAndMalformedSurrogates_rejectsThem() {
        assertThrows(IllegalArgumentException.class, () ->
                TaskDescriptionValidator.validate("bad | task"));
        assertThrows(IllegalArgumentException.class, () ->
                TaskDescriptionValidator.validate("bad\u0000task"));
        assertThrows(IllegalArgumentException.class, () ->
                TaskDescriptionValidator.validate("bad\u202Etask"));
        assertThrows(IllegalArgumentException.class, () ->
                TaskDescriptionValidator.validate("bad\uD800"));
    }

    @Test
    void normalizeForDuplicate_reducesSurroundingAndRepeatedSpaces() {
        assertEquals("read book", TaskDescriptionValidator.normalizeForDuplicate("  read   book  "));
    }
}
