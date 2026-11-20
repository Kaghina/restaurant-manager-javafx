package controller;

import enums.CategoriePlat;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import manager.RestaurantData;
import model.Ingredient;
import model.Plat;
import util.Format;

import java.util.List;

/** Onglet Stocks : ingrédients (réapprovisionnement, seuils) et disponibilité des articles de la carte. */
class StockController {

    private final RestaurantData data = RestaurantData.get();
    private final VBox contenu = new VBox(16);

    Node getView() {
        VBox page = Ui.page("Stocks & ruptures",
                "Les stocks sont décrémentés à l'envoi en cuisine. Un plat passe automatiquement en rupture si un ingrédient manque.");
        page.getChildren().add(contenu);
        rafraichir();
        return Ui.defilement(page);
    }

    private void rafraichir() {
        contenu.getChildren().setAll(alertes(), ingredients(), carte());
    }

    // ── Alertes ──────────────────────────────────────────────

    private Node alertes() {
        List<Ingredient> bas = data.ingredientsStockBas();
        List<Plat> indispo = data.platsIndisponibles();
        if (bas.isEmpty() && indispo.isEmpty())
            return Ui.label("✓ Aucune alerte de stock.", "encart-succes");
        VBox box = new VBox(4);
        box.getStyleClass().add("encart-alerte");
        for (Ingredient i : bas)
            box.getChildren().add(new Label((i.estEnRupture() ? "⛔ Rupture : " : "⚠ Stock bas : ") + i.getNom()
                    + " (" + Format.nombre(i.getQuantiteStock()) + " " + i.getUnite() + ")"));
        if (!indispo.isEmpty())
            box.getChildren().add(new Label("⛔ Indisponibles à la carte : "
                    + String.join(", ", indispo.stream().map(Plat::getNom).toList())));
        return box;
    }

    // ── Ingrédients ──────────────────────────────────────────

    private Node ingredients() {
        GridPane g = grille("Ingrédient", "En stock", "Seuil d'alerte", "État", "Réapprovisionner");
        int row = 1;
        for (Ingredient i : data.getIngredients()) {
            Label etat = i.estEnRupture() ? Ui.label("Rupture", "chip-danger")
                    : i.estStockBas() ? Ui.label("Stock bas", "chip-warning") : Ui.label("OK", "chip-success");
            TextField qte = new TextField();
            qte.setPromptText("+ qté");
            qte.setPrefWidth(80);
            Label erreur = new Label();
            Runnable reappro = () -> {
                double q = Format.lireNombre(qte.getText());
                if (Double.isNaN(q) || q <= 0) { Ui.message(erreur, "invalide", false); return; }
                i.reapprovisionner(q);
                rafraichir();
            };
            qte.setOnAction(e -> reappro.run());
            HBox action = new HBox(6, qte, Ui.label(i.getUnite(), "texte-muted"),
                    Ui.bouton("Ajouter", "btn-secondary-sm", e -> reappro.run()), erreur);
            action.setAlignment(Pos.CENTER_LEFT);
            g.addRow(row++, Ui.label(i.getNom(), "ligne-nom"),
                    Ui.label(Format.nombre(i.getQuantiteStock()) + " " + i.getUnite(), "ligne-prix"),
                    Ui.label(Format.nombre(i.getSeuilAlerte()) + " " + i.getUnite(), "texte-muted"),
                    etat, action);
        }
        return Ui.carte(Ui.label("🥕 Ingrédients", "section-titre"), g);
    }

    // ── Carte ────────────────────────────────────────────────

    private Node carte() {
        VBox box = Ui.carte(Ui.label("📋 Disponibilité de la carte", "section-titre"),
                Ui.label("Stock à l'unité : -1 = illimité (géré par les ingrédients).", "texte-aide"));
        for (CategoriePlat cat : CategoriePlat.values()) {
            List<Plat> plats = data.getMenuCategorie(cat);
            if (plats.isEmpty()) continue;
            GridPane g = grille(cat.getLibelle(), "Prix", "État", "Stock unité", "");
            int row = 1;
            for (Plat p : plats) {
                Label etat;
                if (p.isEnRupture())         etat = Ui.label("Rupture manuelle", "chip-danger");
                else if (!p.estDisponible()) etat = Ui.label("Rupture auto", "chip-danger");
                else if (p.getStock() > 0 && p.getStock() <= 3) etat = Ui.label(p.getStock() + " restant(s)", "chip-warning");
                else                         etat = Ui.label("Disponible", "chip-success");
                if (p.getRecette() != null) etat.setTooltip(new Tooltip("Recette : " + p.getRecette().getDescription()));

                Spinner<Integer> stock = new Spinner<>(-1, 999, p.getStock());
                stock.setEditable(true);
                stock.setPrefWidth(90);
                Button appliquer = Ui.bouton("✔", "btn-secondary-sm", e -> {
                    stock.increment(0); // valide la saisie clavier
                    p.setStock(stock.getValue());
                    rafraichir();
                });
                appliquer.setTooltip(new Tooltip("Appliquer le stock"));
                HBox stockBox = new HBox(6, stock, appliquer);
                stockBox.setAlignment(Pos.CENTER_LEFT);

                Button bascule = p.isEnRupture()
                        ? Ui.bouton("Remettre en vente", "btn-mini-success", e -> { p.rendreDisponible(); rafraichir(); })
                        : Ui.bouton("Mettre en rupture", "btn-mini-danger", e -> { p.mettreEnRupture(); rafraichir(); });

                Label nom = Ui.label(p.getNom(), "ligne-nom");
                g.addRow(row++, nom, Ui.label(Format.euros(p.getPrix()), "texte-muted"), etat, stockBox, bascule);
            }
            box.getChildren().add(g);
        }
        return box;
    }

    private GridPane grille(String... colonnes) {
        GridPane g = new GridPane();
        g.setHgap(18);
        g.setVgap(8);
        g.getStyleClass().add("grille");
        for (int i = 0; i < colonnes.length; i++) g.add(Ui.label(colonnes[i], "grid-header"), i, 0);
        ColumnConstraints premiere = new ColumnConstraints();
        premiere.setMinWidth(200);
        g.getColumnConstraints().add(premiere);
        return g;
    }
}
