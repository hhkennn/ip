package herta.parser;

/**
 * Represents one inclusive, one-based task-number range in an archive command.
 *
 * @param start the first selected task number
 * @param end the last selected task number
 */
public record ArchiveRange(int start, int end) {
}
