package controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import manager.RestaurantData;
import model.Commande;
import model.LigneCommande;
import util.Format;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Onglet Historique : toutes les commandes clôturées, avec réimpression du ticket. */
class HistoriqueController {

    private final RestaurantData data = RestaurantData.get();
    private final TableView<Commande> table = new TableView<>();
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final TextField recherche = new TextField();
    private final Label resume = Ui.label("", "texte-muted");

    Node getView() {
        VBox page = Ui.page("Historique des commandes", "Toutes les additions encaissées. Double-cliquez sur une ligne pour revoir le ticket.");

        date.setPromptText("Toutes les dates");
        date.valueProperty().addListener((o, a, n) -> filtrer());
        recherche.setPromptText("Rechercher (table, client, article)…");
        recherche.textProperty().addListener((o, a, n) -> filtrer());
        HBox.setHgrow(recherche, Priority.ALWAYS);
        HBox barre = new HBox(8, Ui.label("Date :", "champ-label"), date,
                Ui.bouton("Aujourd'hui", "btn-secondary", e -> date.setValue(LocalDate.now())),
                Ui.bouton("Toutes", "btn-secondary", e -> date.setValue(null)),
                recherche);
        barre.setAlignment(Pos.CENTER_LEFT);

        construireTable();
        Button ticket = Ui.bouton("📄 Voir le ticket", "btn-primary", e -> afficherTicket(table.getSelectionModel().getSelectedItem()));
        ticket.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button corriger = Ui.bouton("✏ Corriger le paiement", "btn-secondary", e -> {
            Commande c = table.getSelectionModel().getSelectedItem();
            if (c != null && CorrectionPaiementDialog.ouvrir(data.paiementsDe(c))) {
                table.refresh();
                Ui.info("Paiement corrigé", "Le mode de paiement de la commande #" + c.getId() + " a été corrigé.");
            }
        });
        corriger.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button rouvrir = Ui.bouton("↩ Rouvrir la table", "btn-ghost-danger", e -> rouvrir(table.getSelectionModel().getSelectedItem()));
        rouvrir.setTooltip(new Tooltip("Encaissement par erreur : annule les paiements et remet la commande sur sa table (commandes du jour uniquement)"));
        rouvrir.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, c) ->
                rouvrir.setDisable(c == null || !data.peutRouvrir(c)));

        VBox carte = Ui.carte(barre, table, new HBox(10, resume, Ui.espace(), rouvrir, corriger, ticket));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(520);
        page.getChildren().add(carte);
        filtrer();
        return Ui.defilement(page);
    }

    private void construireTable() {
        TableColumn<Commande, String> cDate = colonne("Date", 140, c -> Format.dateHeure(c.getDateCloture()));
        TableColumn<Commande, String> cTable = colonne("Table", 90, c -> c.getTable().getLibelle());
        TableColumn<Commande, String> cClient = colonne("Client", 150, Commande::getNomClient);
        TableColumn<Commande, Number> cCouv = new TableColumn<>("Couverts");
        cCouv.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getNombreCouverts()));
        cCouv.setPrefWidth(80);
        TableColumn<Commande, String> cArticles = colonne("Articles", 320, c -> c.getToutesLignes().stream()
                .map(l -> l.getQuantite() + "× " + l.getPlat().getNom()).collect(Collectors.joining(", ")));
        TableColumn<Commande, Number> cTotal = new TableColumn<>("Total");
        cTotal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getTotal()));
        cTotal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : Format.euros(v.doubleValue()));
            }
        });
        cTotal.setPrefWidth(100);
        TableColumn<Commande, String> cModes = colonne("Paiement", 170, c -> data.paiementsDe(c).stream()
                .flatMap(p -> p.getMontantsParMode().keySet().stream())
                .distinct().map(m -> m.getIcone() + " " + m.getLibelle()).collect(Collectors.joining(", ")));

        List.of(cDate, cTable, cClient, cCouv, cArticles, cTotal, cModes).forEach(table.getColumns()::add);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucune commande pour ce filtre."));
        table.setRowFactory(tv -> {
            TableRow<Commande> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) afficherTicket(row.getItem()); });
            return row;
        });
    }

    private static TableColumn<Commande, String> colonne(String titre, double largeur,
                                                         java.util.function.Function<Commande, String> valeur) {
        TableColumn<Commande, String> c = new TableColumn<>(titre);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valeur.apply(cell.getValue())));
        c.setPrefWidth(largeur);
        return c;
    }

    private void filtrer() {
        String q = recherche.getText() == null ? "" : recherche.getText().trim().toLowerCase();
        List<Commande> res = data.getHistorique().stream()
                .filter(c -> date.getValue() == null || c.getDateCloture().toLocalDate().equals(date.getValue()))
                .filter(c -> q.isEmpty() || correspond(c, q))
                .sorted(Comparator.comparing(Commande::getDateCloture).reversed())
                .collect(Collectors.toList());
        table.getItems().setAll(res);
        double total = res.stream().mapToDouble(Commande::getTotal).sum();
        resume.setText(res.size() + " commande(s) · " + Format.euros(total));
    }

    private boolean correspond(Commande c, String q) {
        if (c.getTable().getLibelle().toLowerCase().contains(q)) return true;
        if (c.getNomClient().toLowerCase().contains(q)) return true;
        for (LigneCommande l : c.getToutesLignes()) if (l.getPlat().getNom().toLowerCase().contains(q)) return true;
        return false;
    }

    private void rouvrir(Commande c) {
        if (c == null) return;
        if (!Ui.confirmer("Rouvrir la table", "Rouvrir " + c.getTable().getLibelle() + " ? Les paiements de cette commande ("
                + Format.euros(c.getTotal()) + ") seront annulés et la commande reviendra sur la table, prête à être modifiée ou ré-encaissée.")) return;
        try {
            data.rouvrir(c);
        } catch (IllegalStateException ex) {
            Ui.erreur("Rouvrir la table", ex.getMessage());
            return;
        }
        filtrer();
        Ui.info("Table rouverte", c.getTable().getLibelle() + " est de nouveau ouverte : retrouvez-la dans l'onglet « Salle & commandes ».");
    }

    private void afficherTicket(Commande c) {
        if (c == null) return;
        String texte = data.ticket(c);
        TextArea ticket = new TextArea(texte);
        ticket.setEditable(false);
        ticket.getStyleClass().add("ticket-area");
        ticket.setPrefRowCount(26);
        ticket.setPrefColumnCount(42);
        ButtonType imprimer = new ButtonType("🖨 Imprimer", ButtonBar.ButtonData.LEFT);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Ticket — commande #" + c.getId());
        d.getDialogPane().setContent(ticket);
        d.getDialogPane().getButtonTypes().addAll(imprimer, ButtonType.CLOSE);
        Ui.styler(d);
        d.showAndWait().filter(b -> b == imprimer).ifPresent(b -> SalleController.imprimer(texte));
    }
}
