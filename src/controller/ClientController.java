package controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import manager.RestaurantData;
import model.Client;
import model.Reservation;
import util.Format;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Onglet Clients : fichier clients, fidélité et dernières réservations. */
class ClientController {

    private final RestaurantData data = RestaurantData.get();
    private final TableView<Client> table = new TableView<>();
    private final TextField recherche = new TextField();
    private final Label detail = Ui.label("Sélectionnez un client pour voir ses réservations.", "texte-muted");

    Node getView() {
        VBox page = Ui.page("Clients", "Clients enregistrés lors des réservations. ⭐ = client fidèle (5 visites ou plus).");
        recherche.setPromptText("Rechercher un nom, un téléphone, un email…");
        recherche.textProperty().addListener((o, a, n) -> filtrer());
        HBox.setHgrow(recherche, Priority.ALWAYS);

        table.getColumns().add(colonne("Nom", 190, c -> (c.estFidele() ? "⭐ " : "") + c.getNomComplet()));
        table.getColumns().add(colonne("Téléphone", 130, Client::getTelephone));
        table.getColumns().add(colonne("Email", 210, Client::getEmail));
        TableColumn<Client, Number> visites = new TableColumn<>("Visites");
        visites.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNombreVisites()));
        visites.setPrefWidth(80);
        table.getColumns().add(visites);
        table.getColumns().add(colonne("Notes", 260, Client::getNotes));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun client."));
        table.setPrefHeight(380);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> afficherDetail(c));
        detail.setWrapText(true);

        page.getChildren().add(Ui.carte(new HBox(8, recherche), table, detail));
        filtrer();
        return Ui.defilement(page);
    }

    private static TableColumn<Client, String> colonne(String titre, double largeur, Function<Client, String> f) {
        TableColumn<Client, String> c = new TableColumn<>(titre);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(f.apply(cell.getValue())));
        c.setPrefWidth(largeur);
        return c;
    }

    private void filtrer() {
        String q = recherche.getText() == null ? "" : recherche.getText().trim().toLowerCase();
        String qTel = RestaurantData.normaliserTel(q);
        table.getItems().setAll(data.getClients().stream()
                .filter(c -> q.isEmpty()
                        || c.getNomComplet().toLowerCase().contains(q)
                        || c.getEmail().toLowerCase().contains(q)
                        || (!qTel.isEmpty() && RestaurantData.normaliserTel(c.getTelephone()).contains(qTel)))
                .sorted(Comparator.comparingInt(Client::getNombreVisites).reversed().thenComparing(Client::getNom))
                .collect(Collectors.toList()));
    }

    private void afficherDetail(Client c) {
        if (c == null) return;
        String resas = data.getReservations().stream()
                .filter(r -> r.getClient() == c)
                .sorted(Comparator.comparing(Reservation::getDateHeure).reversed())
                .map(r -> "• " + Format.dateHeure(r.getDateHeure()) + " — " + r.getTable().getLibelle()
                        + ", " + r.getNombrePersonnes() + " pers. (" + r.getStatut().getLibelle() + ")")
                .collect(Collectors.joining("\n"));
        detail.setText(c.getNomComplet() + " — " + c.getNombreVisites() + " visite(s)\n"
                + (resas.isEmpty() ? "Aucune réservation enregistrée." : resas));
    }
}
