package herta;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

/**
 * Displays a message with an optional Herta avatar.
 */
public class DialogBox extends HBox {
    private final Label text;
    private final ImageView displayPicture;

    /**
     * Creates a message without an avatar.
     *
     * @param message the message to display
     */
    public DialogBox(String message) {
        this(message, null);
    }

    /**
     * Creates a message with an optional avatar.
     *
     * @param message the message to display
     * @param image the avatar image, or {@code null} for no avatar
     */
    public DialogBox(String message, Image image) {
        text = new Label(message);
        displayPicture = image == null ? null : new ImageView(image);

        //Styling the dialog box
        text.setWrapText(true);
        this.setAlignment(Pos.TOP_RIGHT);

        if (displayPicture == null) {
            this.getChildren().add(text);
        } else {
            displayPicture.setFitWidth(100.0);
            displayPicture.setFitHeight(100.0);
            this.getChildren().addAll(text, displayPicture);
        }
    }
}
