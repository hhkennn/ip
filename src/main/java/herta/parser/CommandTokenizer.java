package herta.parser;

import herta.exception.HertaException;

/** Normalizes the command boundary while preserving spaces inside task text. */
public final class CommandTokenizer {
    /** Maximum command length accepted from either the console or GUI. */
    public static final int MAX_COMMAND_LENGTH = 4_096;

    private CommandTokenizer() {
        // Utility class; do not instantiate.
    }

    /**
     * Normalizes one raw command before recognition and argument extraction.
     * Only the outer boundary and the separator after the command keyword are changed;
     * meaningful spaces in the remaining argument are preserved.
     *
     * @param input the raw command
     * @return the normalized command
     * @throws HertaException if the input is null, too long, or contains unsupported characters
     */
    public static String normalize(String input) throws HertaException {
        validateInput(input);
        String trimmedInput = trimHorizontalWhitespace(input);
        if (trimmedInput.isEmpty()) {
            return trimmedInput;
        }

        int keywordEnd = findFirstWhitespace(trimmedInput);
        if (keywordEnd == trimmedInput.length()) {
            return trimmedInput;
        }
        String keyword = trimmedInput.substring(0, keywordEnd);
        String arguments = trimHorizontalWhitespace(trimmedInput.substring(keywordEnd));
        return arguments.isEmpty() ? keyword : keyword + " " + arguments;
    }

    /** Indicates whether a character is one of the two supported horizontal separators. */
    static boolean isHorizontalWhitespace(char character) {
        return character == ' ' || character == '\t';
    }

    /** Validates raw command size and characters before any parser work occurs. */
    private static void validateInput(String input) throws HertaException {
        if (input == null) {
            throw new HertaException("A command cannot be null. Try entering a Herta command.");
        }
        if (input.length() > MAX_COMMAND_LENGTH) {
            throw new HertaException("That command is too long. Keep it under "
                    + MAX_COMMAND_LENGTH + " characters.");
        }
        for (int i = 0; i < input.length(); i++) {
            char character = input.charAt(i);
            if (isUnsupportedCharacter(character)) {
                throw new HertaException(
                        "Use ordinary spaces and one command per line; lookalike whitespace is unsupported.");
            }
        }
    }

    /** Identifies line breaks, controls, lookalike separators, and formatting overrides. */
    private static boolean isUnsupportedCharacter(char character) {
        boolean isLineBreak = character == '\n' || character == '\r';
        boolean isUnsupportedControl = Character.isISOControl(character)
                && !isHorizontalWhitespace(character);
        boolean isLookalikeSpace = Character.isSpaceChar(character)
                && !isHorizontalWhitespace(character);
        boolean isFormatCharacter = Character.getType(character) == Character.FORMAT;
        return isLineBreak || isUnsupportedControl || isLookalikeSpace || isFormatCharacter;
    }

    /** Trims only the horizontal whitespace supported by the command grammar. */
    private static String trimHorizontalWhitespace(String input) {
        int start = 0;
        int end = input.length();
        while (start < end && isHorizontalWhitespace(input.charAt(start))) {
            start++;
        }
        while (end > start && isHorizontalWhitespace(input.charAt(end - 1))) {
            end--;
        }
        return input.substring(start, end);
    }

    /** Returns the first separator after the command keyword. */
    private static int findFirstWhitespace(String input) {
        for (int i = 0; i < input.length(); i++) {
            if (isHorizontalWhitespace(input.charAt(i))) {
                return i;
            }
        }
        return input.length();
    }
}
