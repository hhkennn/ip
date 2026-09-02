package herta;

import java.util.Objects;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
    private Label userInputPrompt;
    @FXML
    private Button sendButton;

    private Herta herta;
    private final Image hertaImage = new Image(
            Objects.requireNonNull(MainWindow.class.getResourceAsStream("/images/herta.png")));

    /**
     * Binds the scroll position to the dialog container and displays Herta's opening messages.
     */
    @FXML
    public void initialize() {
        scrollPane.vvalueProperty().bind(dialogContainer.heightProperty());
        dialogContainer.getChildren().add(
                DialogBox.getHertaDialog(
                        "Oh, you're here. I'm Herta.\nWell? What do you want?",
                        hertaImage));
        userInputPrompt.visibleProperty().bind(
                userInput.textProperty().isEmpty()
                        .and(userInput.disabledProperty().not()));
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
        HertaResponse hertaResponse = herta.getResponse(userText);
        dialogContainer.getChildren().addAll(
                DialogBox.getUserDialog(userText),
                DialogBox.getHertaDialog(
                        hertaResponse.getMessage(), hertaImage, hertaResponse.getResponseCategory()));
        userInput.clear();

        if (hertaResponse.isExitRequested()) {
            userInput.setDisable(true);
            sendButton.setDisable(true);

            PauseTransition exitDelay = new PauseTransition(Duration.seconds(2));
            exitDelay.setOnFinished(event -> Platform.exit());
            exitDelay.play();
        }
    }
}
