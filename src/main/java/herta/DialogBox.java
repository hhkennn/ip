package herta;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
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

    /**
     * Flips the dialog box such that the ImageView is on the left and text on the right.
     */
    private void flip() {
        this.setAlignment(Pos.TOP_LEFT);
        ObservableList<Node> tmp = FXCollections.observableArrayList(this.getChildren());
        FXCollections.reverse(tmp);
        this.getChildren().setAll(tmp);
    }

    /**
     * Creates a dialog box for a user message without an avatar.
     *
     * @param message the user's message
     * @return a dialog box displaying the user's message
     */
    public static DialogBox getUserDialog(String message) {
        return new DialogBox(message);
    }

    /**
     * Creates a dialog box for a Herta response with an avatar.
     *
     * @param message Herta's response
     * @param image Herta's avatar image
     * @return a dialog box displaying Herta's response
     */
    public static DialogBox getHertaDialog(String message, Image image) {
        var dialogBox = new DialogBox(message, image);
        dialogBox.flip();
        return dialogBox;
    }
}
