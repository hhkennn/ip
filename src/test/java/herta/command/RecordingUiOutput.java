package herta.command;

import java.util.ArrayList;
import java.util.List;

import herta.ui.UiOutput;

/** Records command output in insertion order for focused command tests. */
final class RecordingUiOutput implements UiOutput {
    private final List<String> messages = new ArrayList<>();

    @Override
    public void showMessage(String message) {
        messages.add(message);
    }

    @Override
    public void showGoodbye() {
        messages.add(UiOutput.GOODBYE_MESSAGE);
    }

    /** Returns an immutable snapshot of the recorded messages. */
    List<String> getMessages() {
        return List.copyOf(messages);
    }
}
