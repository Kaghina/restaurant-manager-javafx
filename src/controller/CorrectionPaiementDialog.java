package controller;

import enums.ModePaiement;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import model.Paiement;
import util.Format;

import java.util.*;

/**
 * Correction des modes de paiement d'un ou plusieurs paiements (ex : saisi en espèces au lieu de la carte).
 * Une ligne par paiement, une colonne par mode ; chaque ligne doit totaliser le montant dû.
 */
final class CorrectionPaiementDialog {
    private CorrectionPaiementDialog() { }

    /** @return true si au moins un paiement a été modifié. */
    static boolean ouvrir(List<Paiement> paiements) {
        if (paiements.isEmpty()) {
            Ui.info("Corriger le paiement", "Aucun paiement à corriger.");
            return false;
        }
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.add(Ui.label("Heure", "grid-header"), 0, 0);
        g.add(Ui.label("Montant dû", "grid-header"), 1, 0);
        ModePaiement[] modes = ModePaiement.values();
        for (int c = 0; c < modes.length; c++)
            g.add(Ui.label(modes[c].getIcone() + " " + modes[c].getLibelle(), "grid-header"), c + 2, 0);

        Map<Paiement, Map<ModePaiement, TextField>> champs = new LinkedHashMap<>();
        int row = 1;
        for (Paiement p : paiements) {
            g.add(Ui.label(Format.heure(p.getDateHeure()), "texte-muted"), 0, row);
            g.add(Ui.label(Format.euros(p.getMontantDu()), "ligne-prix"), 1, row);
            Map<ModePaiement, TextField> ligne = new EnumMap<>(ModePaiement.class);
            for (int c = 0; c < modes.length; c++) {
                Double v = p.getMontantsParMode().get(modes[c]);
                TextField f = new TextField(v == null ? "" : Format.nombre(Format.arrondi(v)));
                f.setPromptText("0");
                f.setPrefWidth(100);
                ligne.put(modes[c], f);
                g.add(f, c + 2, row);
            }
            champs.put(p, ligne);
            row++;
        }

        Label erreur = new Label();
        erreur.setWrapText(true);
        VBox contenu = new VBox(12,
                Ui.label("Saisissez la bonne répartition. Chaque ligne doit totaliser le montant dû.", "texte-muted"),
                g, erreur);
        contenu.setPadding(new Insets(8));

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Corriger le mode de paiement");
        d.setHeaderText("Corriger le mode de paiement");
        d.getDialogPane().setContent(contenu);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.styler(d);

        // Validation avant fermeture : on bloque le bouton OK si la saisie est incohérente
        Map<Paiement, Map<ModePaiement, Double>> resultat = new LinkedHashMap<>();
        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            resultat.clear();
            for (Map.Entry<Paiement, Map<ModePaiement, TextField>> e : champs.entrySet()) {
                Map<ModePaiement, Double> montants = new EnumMap<>(ModePaiement.class);
                double total = 0;
                for (Map.Entry<ModePaiement, TextField> f : e.getValue().entrySet()) {
                    String txt = f.getValue().getText();
                    if (txt == null || txt.isBlank()) continue;
                    double v = Format.lireNombre(txt);
                    if (Double.isNaN(v) || v < 0) {
                        Ui.message(erreur, "Montant invalide : « " + txt + " ».", false);
                        ev.consume();
                        return;
                    }
                    montants.put(f.getKey(), Format.arrondi(v));
                    total += v;
                }
                if (Math.abs(total - e.getKey().getMontantDu()) > 0.005) {
                    Ui.message(erreur, "Le paiement de " + Format.heure(e.getKey().getDateHeure()) + " totalise "
                            + Format.euros(total) + " au lieu de " + Format.euros(e.getKey().getMontantDu()) + ".", false);
                    ev.consume();
                    return;
                }
                resultat.put(e.getKey(), montants);
            }
        });

        Optional<ButtonType> r = d.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return false;
        resultat.forEach(Paiement::corrigerModes);
        return true;
    }
}
