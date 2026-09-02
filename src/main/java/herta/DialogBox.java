package herta;

import java.io.IOException;
import java.util.Collections;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
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
    @FXML
    private Label dialog;
    @FXML
    private ImageView displayPicture;

    private DialogBox(String text, Image image) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainWindow.class.getResource("/view/DialogBox.fxml"));
            fxmlLoader.setController(this);
            fxmlLoader.setRoot(this);
            fxmlLoader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load the dialog box.", e);
        }

        dialog.setText(text);
        displayPicture.setImage(image);

        if (image == null) {
            displayPicture.setVisible(false);
            displayPicture.setManaged(false);
        }
    }

    /**
     * Flips the dialog box such that the ImageView is on the left and text on the right.
     */
    private void flip() {
        ObservableList<Node> children = FXCollections.observableArrayList(getChildren());
        Collections.reverse(children);
        getChildren().setAll(children);
        setAlignment(Pos.TOP_LEFT);
        dialog.getStyleClass().add("reply-label");
    }

    /**
     * Creates a dialog box for a user message without an avatar.
     *
     * @param text the user's message
     * @return a dialog box displaying the user's message
     */
    public static DialogBox getUserDialog(String text) {
        return new DialogBox(text, null);
    }

    /**
     * Creates a dialog box for a Herta response with an avatar.
     *
     * @param text Herta's response
     * @param image Herta's avatar image
     * @return a dialog box displaying Herta's response
     */
    public static DialogBox getHertaDialog(String text, Image image) {
        return getHertaDialog(text, image, null);
    }

    /**
     * Creates a dialog box for a Herta response with its semantic response style.
     *
     * @param text Herta's response
     * @param image Herta's avatar image
     * @param responseCategory the semantic category of the response
     * @return a dialog box displaying Herta's response
     */
    public static DialogBox getHertaDialog(String text, Image image, ResponseCategory responseCategory) {
        var dialogBox = new DialogBox(text, image);
        dialogBox.flip();
        dialogBox.applyResponseStyle(responseCategory);
        return dialogBox;
    }

    /**
     * Applies a semantic style to Herta's response label without changing its
     * default styling when no supported category is available.
     *
     * @param responseCategory the semantic category of the response
     */
    private void applyResponseStyle(ResponseCategory responseCategory) {
        if (responseCategory == null) {
            return;
        }

        if (responseCategory == ResponseCategory.ERROR) {
            dialog.getStyleClass().add("error-label");
            return;
        }

        String styleClass = switch (responseCategory) {
            case ADD -> "add-label";
            case MARK -> "marked-label";
            case UNMARK -> "unmarked-label";
            case DELETE -> "delete-label";
            case QUERY -> "query-label";
            case EXIT -> "exit-label";
            default -> null;
        };

        if (styleClass != null) {
            dialog.getStyleClass().add(styleClass);
        }
    }
}
