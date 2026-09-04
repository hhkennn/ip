package herta;

import herta.parser.CommandType;

/**
 * Represents the semantic category of a successfully processed command
 * response.
 */
public enum ResponseCategory {
    ADD,
    MARK,
    UNMARK,
    DELETE,
    QUERY,
    EXIT,
    ERROR;

    /**
     * Maps a parsed command type to the category used by presentation layers.
     *
     * @param commandType the parsed command type
     * @return the corresponding response category, or {@link #ERROR} for an
     *         unsupported command type
     */
    public static ResponseCategory fromCommandType(CommandType commandType) {
        return switch (commandType) {
            case TODO, DEADLINE, EVENT -> ADD;
            case MARK -> MARK;
            case UNMARK -> UNMARK;
            case DELETE -> DELETE;
            case LIST, FIND, FILTER, UPCOMING, SORT -> QUERY;
            case BYE -> EXIT;
            default -> ERROR;
        };
    }
}
