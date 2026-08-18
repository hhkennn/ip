/**
 * Represents the commands supported by Herta.
 */
public enum CommandType {
    TODO("todo", true),
    DEADLINE("deadline", true),
    EVENT("event", true),
    LIST("list", false),
    MARK("mark", true),
    UNMARK("unmark", true),
    DELETE("delete", true),
    BYE("bye", false),
    UNKNOWN("", false);

    private final String keyword;
    private final boolean acceptsArguments;

    /**
     * Creates a command type.
     *
     * @param keyword the text used to identify the command
     * @param acceptsArguments whether the command may be followed by arguments
     */
    CommandType(String keyword, boolean acceptsArguments) {
        this.keyword = keyword;
        this.acceptsArguments = acceptsArguments;
    }

    /**
     * Identifies the command represented by the user's input.
     *
     * @param input the complete user input
     * @return the corresponding command type, or {@link #UNKNOWN}
     */
    public static CommandType fromInput(String input) {
        String trimmedInput = input.trim();
        CommandType[] commands = CommandType.values();

        for (int i = 0; i < commands.length; i++) {
            CommandType command = commands[i];

            if (command == UNKNOWN) {
                continue;
            }

            boolean isExactMatch = trimmedInput.equals(command.keyword);
            boolean hasArguments = trimmedInput.startsWith(command.keyword + " ");

            if (isExactMatch || (command.acceptsArguments && hasArguments)) {
                return command;
            }
        }

        return UNKNOWN;
    }
}
