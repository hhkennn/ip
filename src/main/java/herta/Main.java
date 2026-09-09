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
    private static final double MINIMUM_WINDOW_HEIGHT = 450.0;
    private static final double MINIMUM_WINDOW_WIDTH = 400.0;

    private final Herta herta = new Herta();

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/view/MainWindow.fxml"));
            AnchorPane mainWindow = fxmlLoader.load();
            Scene scene = new Scene(mainWindow);

            stage.setTitle("Herta");
            Image appIcon = new Image(
                    Objects.requireNonNull(MainWindow.class.getResourceAsStream(
                            MainWindow.HERTA_IMAGE_RESOURCE)));
            stage.getIcons().add(appIcon);
            stage.setMinHeight(MINIMUM_WINDOW_HEIGHT);
            stage.setMinWidth(MINIMUM_WINDOW_WIDTH);
            stage.setScene(scene);
            fxmlLoader.<MainWindow>getController().setHerta(herta);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load the main window.", e);
        }
    }
}
