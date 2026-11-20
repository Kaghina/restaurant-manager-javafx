package controller;

import app.MainApp;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Petites fabriques de composants pour garder les vues lisibles et le style dans le CSS. */
final class Ui {
    private Ui() { }

    static Label label(String texte, String... styleClasses) {
        Label l = new Label(texte);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    static Button bouton(String texte, String styleClass, EventHandler<ActionEvent> action) {
        Button b = new Button(texte);
        b.getStyleClass().add(styleClass);
        if (action != null) b.setOnAction(action);
        return b;
    }

    static Region espace() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    static VBox carte(Node... enfants) {
        VBox v = new VBox(10, enfants);
        v.getStyleClass().add("card");
        return v;
    }

    static ScrollPane defilement(Node contenu) {
        ScrollPane sp = new ScrollPane(contenu);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    static VBox page(String titre, String sousTitre) {
        VBox page = new VBox(16);
        page.setPadding(new Insets(24));
        page.getStyleClass().add("page");
        VBox entete = new VBox(2, label(titre, "page-titre"));
        if (sousTitre != null) entete.getChildren().add(label(sousTitre, "page-sous-titre"));
        page.getChildren().add(entete);
        return page;
    }

    static void styler(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(MainApp.css());
        dialog.getDialogPane().getStyleClass().add("dialogue");
    }

    static void info(String titre, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.setTitle(titre);
        a.setHeaderText(null);
        styler(a);
        a.showAndWait();
    }

    static void erreur(String titre, String message) {
        Alert a = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        a.setTitle(titre);
        a.setHeaderText(null);
        styler(a);
        a.showAndWait();
    }

    static boolean confirmer(String titre, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.setTitle(titre);
        a.setHeaderText(null);
        styler(a);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }

    static void message(Label cible, String texte, boolean succes) {
        cible.setText(texte);
        cible.getStyleClass().removeAll("msg-ok", "msg-erreur");
        cible.getStyleClass().add(succes ? "msg-ok" : "msg-erreur");
    }
}
