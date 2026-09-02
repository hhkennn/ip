package herta;

import java.util.Objects;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

/**
 * Controls the main GUI window for Herta.
 */
public class MainWindow extends AnchorPane {
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox dialogContainer;
    @FXML
    private TextField userInput;
    @FXML
    private Button sendButton;

    private Herta herta;
    private final Image hertaImage = new Image(
            Objects.requireNonNull(MainWindow.class.getResourceAsStream("/images/herta.png")));

    /**
     * Binds the scroll position to the height of the dialog container after FXML injection.
     */
    @FXML
    public void initialize() {
        scrollPane.vvalueProperty().bind(dialogContainer.heightProperty());
    }

    /**
     * Supplies the Herta instance used to process GUI commands.
     *
     * @param herta the Herta instance to use
     */
    public void setHerta(Herta herta) {
        this.herta = herta;
    }

    /**
     * Displays the user's command and Herta's response, then clears the input field.
     */
    @FXML
    private void handleUserInput() {
        String userText = userInput.getText();
        String hertaText = herta.getResponse(userText);
        dialogContainer.getChildren().addAll(
                DialogBox.getUserDialog(userText),
                DialogBox.getHertaDialog(hertaText, hertaImage));
        userInput.clear();
    }
}
