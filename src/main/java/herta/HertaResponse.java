package herta;

/**
 * Represents the result of processing one command for the graphical user interface.
 */
public class HertaResponse {
    private final String message;
    private final boolean exitRequested;

    /**
     * Creates a response with its message and exit status.
     *
     * @param message the message to display
     * @param exitRequested whether the application should exit
     */
    public HertaResponse(String message, boolean exitRequested) {
        this.message = message;
        this.exitRequested = exitRequested;
    }

    /**
     * Returns the message generated while processing the command.
     *
     * @return the response message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Indicates whether the command requested application exit.
     *
     * @return {@code true} if the application should exit
     */
    public boolean isExitRequested() {
        return exitRequested;
    }
}
