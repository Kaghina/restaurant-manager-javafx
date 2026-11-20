package controller;

import enums.ModePaiement;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import manager.RestaurantData;
import model.Commande;
import model.Paiement;
import service.StatistiqueService;
import util.Format;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/** Onglet Statistiques : indicateurs de la journée, ventes, et prédiction des heures de rush. */
class StatistiquesController {

    private final StatistiqueService stats = new StatistiqueService(RestaurantData.get());
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final VBox contenu = new VBox(16);

    Node getView() {
        VBox page = Ui.page("Statistiques", "Chiffre d'affaires, couverts, meilleures ventes et prévision d'affluence.");
        Button precedent = Ui.bouton("◀", "btn-secondary", e -> date.setValue(jourRef().minusDays(1)));
        Button suivant = Ui.bouton("▶", "btn-secondary", e -> date.setValue(jourRef().plusDays(1)));
        Button aujourdhui = Ui.bouton("Aujourd'hui", "btn-secondary", e -> date.setValue(LocalDate.now()));
        Button tout = Ui.bouton("Tout l'historique", "btn-secondary", e -> date.setValue(null));
        date.setPromptText("Tout l'historique");
        date.valueProperty().addListener((o, a, n) -> rafraichir());
        HBox barre = new HBox(8, Ui.label("Période :", "champ-label"), precedent, date, suivant, aujourdhui, tout);
        barre.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().addAll(barre, contenu);
        rafraichir();
        return Ui.defilement(page);
    }

    private LocalDate jourRef() { return date.getValue() == null ? LocalDate.now() : date.getValue(); }

    private void rafraichir() {
        List<Paiement> paiements = stats.paiements(date.getValue());
        Set<Commande> commandes = stats.commandes(paiements);
        double ca = stats.chiffreAffaires(paiements);
        int couverts = stats.couverts(commandes);

        FlowPane kpis = new FlowPane(12, 12,
                kpi("💰 Chiffre d'affaires", Format.euros(ca), "kpi-ca"),
                kpi("📄 Additions", String.valueOf(commandes.size()), "kpi-normal"),
                kpi("👥 Couverts", String.valueOf(couverts), "kpi-normal"),
                kpi("🎯 Ticket moyen", Format.euros(stats.ticketMoyen(paiements)), "kpi-normal"),
                kpi("🍽 Moyenne / couvert", Format.euros(couverts == 0 ? 0 : ca / couverts), "kpi-normal"));

        FlowPane modes = new FlowPane(12, 12);
        for (Map.Entry<ModePaiement, Double> e : stats.parMode(paiements).entrySet()) {
            double part = ca == 0 ? 0 : e.getValue() / ca * 100;
            modes.getChildren().add(kpi(e.getKey().getIcone() + " " + e.getKey().getLibelle(),
                    Format.euros(e.getValue()), "kpi-mode", Format.nombre(Math.round(part)) + " % du CA"));
        }

        HBox graphes = new HBox(16, carteCaParHeure(paiements), carteTop(commandes));
        graphes.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        HBox prevision = new HBox(16, cartePrevision(), carteJours());
        prevision.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        contenu.getChildren().setAll(kpis,
                Ui.carte(Ui.label("Répartition par mode de paiement", "section-titre"), modes),
                graphes, prevision);
    }

    private VBox kpi(String libelle, String valeur, String style, String... detail) {
        VBox card = new VBox(4, Ui.label(libelle, "kpi-label"), Ui.label(valeur, "kpi-value"));
        if (detail.length > 0) card.getChildren().add(Ui.label(detail[0], "texte-aide"));
        card.getStyleClass().addAll("kpi-card", style);
        card.setPrefWidth(190);
        return card;
    }

    private VBox carteCaParHeure(List<Paiement> paiements) {
        double[] ca = stats.caParHeure(paiements);
        BarChart<String, Number> chart = graphe("CA (€)");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int h = StatistiqueService.HEURE_DEBUT; h <= StatistiqueService.HEURE_FIN; h++)
            s.getData().add(new XYChart.Data<>(h + "h", Format.arrondi(ca[h])));
        chart.getData().add(s);
        int pic = StatistiqueService.heurePic(ca);
        return Ui.carte(Ui.label("Chiffre d'affaires par heure d'arrivée", "section-titre"),
                Ui.label(pic < 0 ? "Aucune vente sur la période." : "Heure la plus rentable : " + pic + "h – " + (pic + 1) + "h", "texte-muted"),
                chart);
    }

    private VBox carteTop(Set<Commande> commandes) {
        LinkedHashMap<String, Integer> top = stats.topArticles(commandes, 8);
        VBox lignes = new VBox(6);
        int max = top.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        int rang = 1;
        for (Map.Entry<String, Integer> e : top.entrySet()) {
            ProgressBar barre = new ProgressBar((double) e.getValue() / max);
            barre.setMaxWidth(Double.MAX_VALUE);
            barre.getStyleClass().add("barre-top");
            HBox.setHgrow(barre, Priority.ALWAYS);
            Label nom = Ui.label(rang++ + ". " + e.getKey(), "ligne-nom");
            nom.setMinWidth(190);
            nom.setPrefWidth(190);
            HBox row = new HBox(10, nom, barre, Ui.label("× " + e.getValue(), "ligne-prix"));
            row.setAlignment(Pos.CENTER_LEFT);
            lignes.getChildren().add(row);
        }
        if (top.isEmpty()) lignes.getChildren().add(Ui.label("Aucune vente sur la période.", "texte-vide"));
        VBox carte = Ui.carte(Ui.label("🏆 Articles les plus vendus", "section-titre"), lignes);
        carte.setMinWidth(380);
        return carte;
    }

    private VBox cartePrevision() {
        LocalDate jour = jourRef();
        double[] prev = stats.previsionCouverts(jour);
        long reference = stats.joursDeReference(jour);
        BarChart<String, Number> chart = graphe("Couverts");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int h = StatistiqueService.HEURE_DEBUT; h <= StatistiqueService.HEURE_FIN; h++)
            s.getData().add(new XYChart.Data<>(h + "h", Math.round(prev[h] * 10) / 10.0));
        chart.getData().add(s);
        chart.getStyleClass().add("graphe-prevision");

        String nomJour = jour.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRANCE);
        double total = Arrays.stream(prev).sum();
        int pic = StatistiqueService.heurePic(prev);
        Label resume;
        if (reference == 0) {
            resume = Ui.label("Pas assez d'historique pour un " + nomJour + ".", "texte-muted");
        } else {
            resume = Ui.label("🔥 Rush prévu entre " + pic + "h et " + (pic + 1) + "h (≈ " + Math.round(prev[pic])
                    + " couverts) · ≈ " + Math.round(total) + " couverts sur la journée", "encart-info");
        }
        return Ui.carte(Ui.label("📈 Prévision d'affluence — " + nomJour + " " + jour.format(Format.DATE), "section-titre"),
                Ui.label("Moyenne des " + reference + " " + nomJour + "(s) précédent(s) de l'historique.", "texte-aide"),
                resume, chart);
    }

    private VBox carteJours() {
        Map<DayOfWeek, Double> moy = stats.couvertsMoyensParJour();
        BarChart<String, Number> chart = graphe("Couverts / jour");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        DayOfWeek best = null;
        for (DayOfWeek d : DayOfWeek.values()) {
            double v = moy.getOrDefault(d, 0.0);
            s.getData().add(new XYChart.Data<>(d.getDisplayName(TextStyle.SHORT, Locale.FRANCE), Math.round(v)));
            if (v > 0 && (best == null || v > moy.get(best))) best = d;
        }
        chart.getData().add(s);
        return Ui.carte(Ui.label("📅 Affluence moyenne par jour", "section-titre"),
                Ui.label(best == null ? "Pas encore d'historique." :
                        "Jour le plus chargé : " + best.getDisplayName(TextStyle.FULL, Locale.FRANCE), "texte-muted"),
                chart);
    }

    private BarChart<String, Number> graphe(String axeY) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel(axeY);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        chart.setMinWidth(380);
        chart.setCategoryGap(6);
        return chart;
    }
}
