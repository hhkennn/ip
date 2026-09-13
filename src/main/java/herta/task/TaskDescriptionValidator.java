package herta.task;

import java.util.Objects;

/** Validates task descriptions shared by command parsing and storage loading. */
public final class TaskDescriptionValidator {
    /** Maximum number of UTF-16 code units allowed in a task description. */
    public static final int MAX_DESCRIPTION_LENGTH = 1_000;

    private static final char STORAGE_FIELD_DELIMITER = '|';

    private TaskDescriptionValidator() {
        // Utility class; do not instantiate.
    }

    /**
     * Validates a task description before it enters the domain model.
     *
     * @param description the description to validate
     * @throws NullPointerException if the description is null
     * @throws IllegalArgumentException if the description is blank, too long,
     *         or contains a delimiter or unsupported control character
     */
    public static void validate(String description) {
        Objects.requireNonNull(description, "Task description cannot be null.");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Task description cannot be blank.");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Task descriptions cannot exceed "
                    + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        for (int i = 0; i < description.length(); i++) {
            char character = description.charAt(i);
            if (isInvalidCharacter(character)) {
                throw new IllegalArgumentException(
                        "Task descriptions cannot contain `|`, line breaks, or control characters.");
            }
        }
    }

    /** Identifies storage delimiters, line breaks, controls, and unsupported invisible characters. */
    private static boolean isInvalidCharacter(char character) {
        boolean isStorageDelimiter = character == STORAGE_FIELD_DELIMITER;
        boolean isLineBreak = character == '\n' || character == '\r';
        boolean isControlCharacter = Character.isISOControl(character);
        boolean isFormatCharacter = Character.getType(character) == Character.FORMAT;
        boolean isUnsupportedSpace = Character.isSpaceChar(character) && character != ' ';
        return isStorageDelimiter || isLineBreak || isControlCharacter
                || isFormatCharacter || isUnsupportedSpace;
    }

    /**
     * Returns the description form used when comparing tasks for duplicates.
     *
     * @param description the validated description
     * @return a form with surrounding and repeated horizontal spaces normalized
     */
    public static String normalizeForDuplicate(String description) {
        validate(description);
        return description.trim().replaceAll("[ \\t]+", " ");
    }
}
