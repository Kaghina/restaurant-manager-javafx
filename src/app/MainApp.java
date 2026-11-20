package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Objects;

public class MainApp extends Application {

    private static final String VUE = "/ressources/view/main-view.fxml";
    private static final String CSS = "/ressources/style.css";

    /** URL de la feuille de style (réutilisée par les boîtes de dialogue). */
    public static String css() {
        URL url = Objects.requireNonNull(MainApp.class.getResource(CSS), "style.css introuvable");
        return url.toExternalForm();
    }

    @Override
    public void start(Stage stage) throws Exception {
        URL vue = Objects.requireNonNull(getClass().getResource(VUE), "main-view.fxml introuvable");
        Rectangle2D ecran = Screen.getPrimary().getVisualBounds();
        double largeur = Math.min(1360, ecran.getWidth());
        double hauteur = Math.min(840, ecran.getHeight());
        Scene scene = new Scene(new FXMLLoader(vue).load(), largeur, hauteur);
        scene.getStylesheets().add(css());
        stage.setTitle("Restaurant Manager");
        stage.setMinWidth(Math.min(1000, ecran.getWidth()));
        stage.setMinHeight(Math.min(620, ecran.getHeight()));
        stage.setScene(scene);
        // Sur un petit écran, on occupe tout l'espace disponible
        if (ecran.getWidth() < 1400 || ecran.getHeight() < 860) stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
