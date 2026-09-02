package herta;

import java.io.IOException;
import java.util.Objects;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 * Provides the JavaFX user interface entry point for Herta.
 */
public class Main extends Application {

    private final Herta herta = new Herta("data/herta.txt");

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/view/MainWindow.fxml"));
            AnchorPane mainWindow = fxmlLoader.load();
            Scene scene = new Scene(mainWindow);

            stage.setTitle("Herta");
            Image appIcon = new Image(
                    Objects.requireNonNull(Main.class.getResourceAsStream("/images/herta.png")));
            stage.getIcons().add(appIcon);
            stage.setMinHeight(450.0);
            stage.setMinWidth(400.0);
            stage.setScene(scene);
            fxmlLoader.<MainWindow>getController().setHerta(herta);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load the main window.", e);
        }
    }
}
