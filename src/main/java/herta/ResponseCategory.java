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
        switch (commandType) {
            case TODO:
            case DEADLINE:
            case EVENT:
                return ADD;
            case MARK:
                return MARK;
            case UNMARK:
                return UNMARK;
            case DELETE:
                return DELETE;
            case LIST:
            case FIND:
            case FILTER:
            case UPCOMING:
            case SORT:
                return QUERY;
            case BYE:
                return EXIT;
            default:
                return ERROR;
        }
    }
}
