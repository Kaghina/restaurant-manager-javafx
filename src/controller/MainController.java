package controller;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.util.Duration;
import manager.RestaurantData;
import model.Paiement;
import model.TableRestaurant;
import util.Format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

/** Fenêtre principale : en-tête (indicateurs temps réel) + onglets. */
public class MainController {

    private static final DateTimeFormatter HORLOGE = DateTimeFormatter.ofPattern("EEE d MMM  ·  HH:mm", Locale.FRANCE);

    @FXML private TabPane mainTabPane;
    @FXML private Label lblNom;
    @FXML private Label lblTables;
    @FXML private Label lblCa;
    @FXML private Label lblAlertes;
    @FXML private Label lblHorloge;

    private final RestaurantData data = RestaurantData.get();
    private final SalleController salle = new SalleController();
    private Tab ongletStocks;

    @FXML
    public void initialize() {
        lblNom.setText(RestaurantData.NOM_RESTAURANT);

        Tab ongletSalle = new Tab("🍽  Salle & commandes", salle.getView());
        ongletSalle.setOnSelectionChanged(e -> { if (ongletSalle.isSelected()) salle.rafraichir(); });
        mainTabPane.getTabs().add(ongletSalle);

        ajouterOnglet("📅  Réservations", () -> new ReservationController().getView());
        ongletStocks = ajouterOnglet("📦  Stocks", () -> new StockController().getView());
        ajouterOnglet("📊  Statistiques", () -> new StatistiquesController().getView());
        ajouterOnglet("🗂  Historique", () -> new HistoriqueController().getView());
        ajouterOnglet("👥  Clients", () -> new ClientController().getView());

        lblAlertes.setCursor(Cursor.HAND);
        lblAlertes.setOnMouseClicked(e -> mainTabPane.getSelectionModel().select(ongletStocks));

        majEntete();
        Timeline horloge = new Timeline(new KeyFrame(Duration.seconds(2), e -> majEntete()));
        horloge.setCycleCount(Animation.INDEFINITE);
        horloge.play();
        Timeline plan = new Timeline(new KeyFrame(Duration.seconds(60), e -> salle.rafraichirPlanSeul()));
        plan.setCycleCount(Animation.INDEFINITE);
        plan.play();
    }

    /** Onglet dont le contenu est reconstruit à chaque affichage (données toujours à jour). */
    private Tab ajouterOnglet(String titre, Supplier<Node> vue) {
        Tab tab = new Tab(titre);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) tab.setContent(vue.get());
        });
        mainTabPane.getTabs().add(tab);
        return tab;
    }

    private void majEntete() {
        long occupees = data.getTablesSalle().stream().filter(TableRestaurant::estOccupee).count();
        long emporter = data.getTables().stream().filter(t -> t.getZone() == TableRestaurant.Zone.EMPORTER && t.estOccupee()).count();
        lblTables.setText("🍽 " + occupees + "/" + data.getTablesSalle().size() + " tables"
                + (emporter > 0 ? "  ·  🛍 " + emporter : ""));

        LocalDate today = LocalDate.now();
        double ca = data.getPaiements().stream().filter(p -> p.getDate().equals(today))
                .mapToDouble(Paiement::getMontantDu).sum();
        lblCa.setText("💰 " + Format.euros(ca) + " aujourd'hui");

        int alertes = data.ingredientsStockBas().size() + data.platsIndisponibles().size();
        lblAlertes.setText(alertes == 0 ? "✓ Stocks OK" : "⚠ " + alertes + " alerte(s) stock");
        lblAlertes.getStyleClass().setAll("label", alertes == 0 ? "header-chip" : "header-chip-alerte");

        String h = LocalDateTime.now().format(HORLOGE);
        lblHorloge.setText(Character.toUpperCase(h.charAt(0)) + h.substring(1));
    }
}
