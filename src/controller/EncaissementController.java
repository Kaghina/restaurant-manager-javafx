package controller;

import enums.ModePaiement;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import manager.RestaurantData;
import model.Commande;
import model.LigneCommande;
import model.Paiement;
import model.TableRestaurant;
import util.Format;

import java.util.*;

/**
 * Écran d'encaissement d'une commande :
 * sélection des articles à régler (addition séparée), partage d'un article entre plusieurs personnes,
 * remises / offerts, saisie au pavé numérique et combinaison de plusieurs modes de paiement.
 * Les montants déjà réglés sont mémorisés sur chaque ligne : on peut quitter l'écran et revenir sans risque.
 */
class EncaissementController {

    interface Ecouteur {
        void retour();
        void cloture(Commande commande, double rendu);
        void paiementPartiel();
    }

    /** Part payable d'une ligne. Une ligne non partagée = une part dont le montant suit la ligne. */
    private static final class Part {
        final LigneCommande ligne;
        final int numero;
        final int nombre;
        final double montantFixe;

        Part(LigneCommande ligne, int numero, int nombre, double montantFixe) {
            this.ligne = ligne;
            this.numero = numero;
            this.nombre = nombre;
            this.montantFixe = montantFixe;
        }

        double montant() { return nombre == 1 ? ligne.getResteAPayer() : montantFixe; }

        String libelle() {
            String s = ligne.getQuantite() + " × " + ligne.getPlat().getNom();
            return nombre > 1 ? s + "  (" + numero + "/" + nombre + ")" : s;
        }
    }

    private static final String[] REMISES = {"0 %", "5 %", "10 %", "15 %", "20 %", "25 %", "50 %", "100 %"};

    private final RestaurantData data = RestaurantData.get();
    private final TableRestaurant table;
    private final Commande cmd;
    private final Ecouteur ecouteur;

    private final List<Part> parts = new ArrayList<>();
    private final Map<Part, CheckBox> selection = new LinkedHashMap<>();
    private final Map<ModePaiement, Double> saisis = new LinkedHashMap<>();
    private final StringBuilder saisie = new StringBuilder();

    private final VBox listeParts = new VBox(6);
    private final VBox boxSaisis = new VBox(4);
    private final VBox boxHistorique = new VBox(4);
    private final Label lblSelection = Ui.label("", "montant-ligne");
    private final Label lblSaisi = Ui.label("", "montant-ligne");
    private final Label lblReste = Ui.label("", "montant-reste");
    private final Label lblMessage = new Label();
    private final TextField ecran = new TextField("0");

    EncaissementController(TableRestaurant table, Ecouteur ecouteur) {
        this.table = table;
        this.cmd = table.getCommandeEnCours();
        this.ecouteur = ecouteur;
        initialiserParts();
    }

    /** Une part par ligne restant à payer (ou modifiable). */
    private void initialiserParts() {
        parts.clear();
        for (LigneCommande l : cmd.getToutesLignes())
            if (!l.estSoldee() || l.getMontantPaye() < 0.005) parts.add(new Part(l, 1, 1, 0));
    }

    Node getView() {
        // ── Colonne gauche : articles
        Button tout = Ui.bouton("Tout", "btn-lien", e -> { selection.values().forEach(c -> c.setSelected(true)); majTotaux(); });
        Button rien = Ui.bouton("Aucun", "btn-lien", e -> { selection.values().forEach(c -> c.setSelected(false)); majTotaux(); });
        HBox enteteArticles = new HBox(6, Ui.label("Articles à régler", "section-lbl"), Ui.espace(), tout, rien);
        enteteArticles.setAlignment(Pos.CENTER_LEFT);

        ScrollPane spArticles = Ui.defilement(listeParts);
        spArticles.setPrefHeight(300);
        VBox.setVgrow(spArticles, Priority.ALWAYS);

        VBox totaux = new VBox(4, lblSelection, lblSaisi, boxSaisis, lblReste);
        totaux.getStyleClass().add("encart");

        Button retour = Ui.bouton("← Retour à la commande", "btn-secondary", e -> ecouteur.retour());
        VBox gauche = new VBox(12,
                new HBox(10, Ui.label("💳 Encaissement — " + table.getLibelle(), "panneau-titre"), Ui.espace(),
                        Ui.label("Total commande " + Format.euros(cmd.getTotal()), "chip")),
                enteteArticles, spArticles, totaux, boxHistorique, lblMessage, retour);
        gauche.getStyleClass().add("card");
        HBox.setHgrow(gauche, Priority.ALWAYS);

        // ── Colonne droite : pavé + modes
        VBox droite = construireCaisse();

        HBox racine = new HBox(16, gauche, droite);
        racine.setPadding(new Insets(16));

        reconstruireParts();
        majHistorique();
        majTotaux();
        return Ui.defilement(racine);
    }

    // ═══ ARTICLES ═════════════════════════════════════════════

    private void reconstruireParts() {
        Map<Part, Boolean> etat = new HashMap<>();
        selection.forEach((p, cb) -> etat.put(p, cb.isSelected()));
        listeParts.getChildren().clear();
        selection.clear();

        if (parts.isEmpty()) {
            listeParts.getChildren().add(Ui.label("Tous les articles sont réglés.", "texte-muted"));
            return;
        }
        for (Part part : parts) {
            CheckBox cb = new CheckBox(part.libelle());
            cb.setSelected(etat.getOrDefault(part, true));
            cb.setOnAction(e -> majTotaux());
            cb.setMaxWidth(260);
            selection.put(part, cb);

            Label montant = Ui.label(Format.euros(part.montant()), "ligne-prix");
            montant.setMinWidth(72);
            HBox row = new HBox(8, cb, Ui.espace(), montant);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("ligne-paiement");

            boolean modifiable = part.nombre == 1 && part.ligne.getMontantPaye() < 0.005;
            if (modifiable) {
                ComboBox<String> remise = new ComboBox<>();
                remise.getItems().addAll(REMISES);
                remise.setValue(part.ligne.isOffert() ? "100 %" : Format.nombre(part.ligne.getRemisePourcentage()) + " %");
                remise.setPrefWidth(84);
                remise.setTooltip(new Tooltip("Remise sur cet article"));
                remise.setOnAction(e -> {
                    double r = Format.lireNombre(remise.getValue().replace("%", ""));
                    part.ligne.setOffert(false);
                    part.ligne.setRemisePourcentage(Double.isNaN(r) ? 0 : r);
                    reconstruireParts();
                    majTotaux();
                });
                Button offert = Ui.bouton(part.ligne.isOffert() ? "Offert ✓" : "Offrir", part.ligne.isOffert() ? "btn-mini-success" : "btn-mini", e -> {
                    part.ligne.setOffert(!part.ligne.isOffert());
                    if (!part.ligne.isOffert()) part.ligne.setRemisePourcentage(0);
                    reconstruireParts();
                    majTotaux();
                });
                row.getChildren().addAll(remise, offert);
            }
            if (part.montant() > 0.01) {
                Button partager = Ui.bouton("÷", "btn-mini-accent", e -> partager(part));
                partager.setTooltip(new Tooltip("Partager cet article entre plusieurs personnes"));
                row.getChildren().add(partager);
            }
            listeParts.getChildren().add(row);
        }
    }

    private void partager(Part part) {
        TextInputDialog d = new TextInputDialog("2");
        d.setTitle("Partager un article");
        d.setHeaderText(part.libelle() + " — " + Format.euros(part.montant()));
        d.setContentText("Nombre de personnes :");
        Ui.styler(d);
        d.showAndWait().ifPresent(val -> {
            int n;
            try { n = Integer.parseInt(val.trim()); } catch (NumberFormatException ex) { n = 0; }
            if (n < 2 || n > 20) {
                Ui.message(lblMessage, "Indiquez un nombre de personnes entre 2 et 20.", false);
                return;
            }
            double total = part.montant();
            double base = Format.arrondi(total / n);
            int idx = parts.indexOf(part);
            parts.remove(idx);
            for (int i = 1; i <= n; i++) {
                double m = i < n ? base : Format.arrondi(total - base * (n - 1));
                parts.add(idx + i - 1, new Part(part.ligne, i, n, m));
            }
            reconstruireParts();
            majTotaux();
        });
    }

    private List<Part> partsSelectionnees() {
        List<Part> res = new ArrayList<>();
        selection.forEach((p, cb) -> { if (cb.isSelected()) res.add(p); });
        return res;
    }

    private double totalSelection() {
        return Format.arrondi(partsSelectionnees().stream().mapToDouble(Part::montant).sum());
    }

    private double totalSaisi() {
        return Format.arrondi(saisis.values().stream().mapToDouble(Double::doubleValue).sum());
    }

    // ═══ TOTAUX & HISTORIQUE ══════════════════════════════════

    private void majTotaux() {
        double sel = totalSelection();
        double saisi = totalSaisi();
        lblSelection.setText("À régler (sélection) : " + Format.euros(sel));
        lblSaisi.setText("Montant saisi : " + Format.euros(saisi));
        boxSaisis.getChildren().clear();
        saisis.forEach((mode, montant) -> {
            HBox r = new HBox(8, Ui.label(mode.getIcone() + " " + mode.getLibelle(), "ligne-nom"), Ui.espace(),
                    Ui.label(Format.euros(montant), "montant-mode"),
                    Ui.bouton("✕", "btn-mini-danger", e -> { saisis.remove(mode); majTotaux(); }));
            r.setAlignment(Pos.CENTER_LEFT);
            boxSaisis.getChildren().add(r);
        });
        double reste = sel - saisi;
        lblReste.setText(reste >= -0.005 ? "Reste à payer : " + Format.euros(Math.max(0, reste))
                                         : "À rendre : " + Format.euros(-reste));
        if (saisie.length() == 0) ecran.setPromptText(Format.nombre(Math.max(0, Format.arrondi(reste))));
    }

    private void majHistorique() {
        boxHistorique.getChildren().clear();
        List<Paiement> deja = data.paiementsDe(cmd);
        if (deja.isEmpty()) return;
        boxHistorique.getChildren().add(Ui.label("Déjà encaissé", "section-lbl"));
        for (Paiement p : deja) {
            String modes = p.getMontantsParMode().entrySet().stream()
                    .map(e -> e.getKey().getIcone() + " " + e.getKey().getLibelle())
                    .reduce((a, b) -> a + " + " + b).orElse("");
            Button corriger = Ui.bouton("✏", "btn-mini-accent", e -> {
                if (CorrectionPaiementDialog.ouvrir(List.of(p))) {
                    majHistorique();
                    Ui.message(lblMessage, "✓ Mode de paiement corrigé.", true);
                }
            });
            corriger.setTooltip(new Tooltip("Corriger le mode de paiement"));
            Button annuler = Ui.bouton("✕", "btn-mini-danger", e -> {
                if (!Ui.confirmer("Annuler le paiement", "Annuler le paiement de " + Format.euros(p.getMontantDu())
                        + " (" + modes + ") ? Les articles concernés redeviendront à payer.")) return;
                data.annulerPaiement(p);
                initialiserParts();
                selection.clear();
                reconstruireParts();
                majHistorique();
                majTotaux();
                Ui.message(lblMessage, "✓ Paiement annulé.", true);
                ecouteur.paiementPartiel();
            });
            annuler.setTooltip(new Tooltip("Annuler ce paiement"));
            HBox row = new HBox(8, Ui.label(Format.heure(p.getDateHeure()) + "  " + modes, "texte-muted"), Ui.espace(),
                    Ui.label(Format.euros(p.getMontantDu()), "montant-mode"), corriger, annuler);
            row.setAlignment(Pos.CENTER_LEFT);
            boxHistorique.getChildren().add(row);
        }
        boxHistorique.getStyleClass().setAll("encart");
    }

    // ═══ CAISSE (pavé numérique + modes) ══════════════════════

    private VBox construireCaisse() {
        ecran.getStyleClass().add("calc-ecran");
        ecran.setEditable(false);
        ecran.setText("");
        ecran.setPrefHeight(54);

        GridPane pave = new GridPane();
        pave.setHgap(6);
        pave.setVgap(6);
        String[][] touches = {{"7", "8", "9", "÷"}, {"4", "5", "6", "×"}, {"1", "2", "3", "−"}, {"0", ",", "C", "+"}};
        for (int r = 0; r < touches.length; r++) {
            for (int c = 0; c < touches[r].length; c++) {
                String t = touches[r][c];
                Button b = Ui.bouton(t, "C".equals(t) ? "calc-btn-clear" : "÷×−+".contains(t) ? "calc-btn-op" : "calc-btn",
                        e -> touche(t));
                b.setPrefSize(56, 44);
                pave.add(b, c, r);
            }
        }

        HBox billets = new HBox(6);
        for (int v : new int[]{5, 10, 20, 50}) {
            Button b = Ui.bouton(v + " €", "btn-billet", e -> { saisie.setLength(0); saisie.append(v); ecran.setText(saisie.toString()); });
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            billets.getChildren().add(b);
        }

        VBox modes = new VBox(6);
        for (ModePaiement mode : ModePaiement.values()) {
            Button b = Ui.bouton(mode.getIcone() + "  " + mode.getLibelle(), "calc-btn-mode", e -> ajouterMode(mode));
            b.setMaxWidth(Double.MAX_VALUE);
            modes.getChildren().add(b);
        }

        Button valider = Ui.bouton("✔ Valider le paiement", "btn-success", e -> valider());
        valider.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(10,
                Ui.label("🔢 Saisie du montant", "section-lbl"), ecran, pave, billets,
                Ui.label("Laisser vide = payer le reste", "texte-aide"),
                new Separator(), Ui.label("Mode de paiement", "section-lbl"), modes,
                new Separator(), valider);
        box.getStyleClass().add("card");
        box.setPrefWidth(270);
        box.setMinWidth(260);
        return box;
    }

    private void touche(String t) {
        switch (t) {
            case "C" -> saisie.setLength(0);
            case "," -> saisie.append('.');
            default  -> saisie.append(t);
        }
        ecran.setText(saisie.toString().replace('.', ','));
    }

    private void ajouterMode(ModePaiement mode) {
        double reste = Format.arrondi(totalSelection() - totalSaisi());
        double montant = saisie.length() == 0 ? reste : evaluer(saisie.toString());
        if (Double.isNaN(montant) || montant <= 0.004) {
            Ui.message(lblMessage, reste <= 0 ? "Le montant sélectionné est déjà couvert." : "Montant invalide.", false);
            reinitialiserSaisie();
            return;
        }
        montant = Format.arrondi(montant);
        if (mode != ModePaiement.ESPECES && montant > reste + 0.005) {
            Ui.message(lblMessage, "Seules les espèces permettent de rendre la monnaie (max " + Format.euros(Math.max(0, reste)) + " en " + mode.getLibelle() + ").", false);
            return;
        }
        saisis.merge(mode, montant, Double::sum);
        reinitialiserSaisie();
        lblMessage.setText("");
        majTotaux();
        if (totalSelection() > 0 && totalSaisi() + 0.005 >= totalSelection()) valider();
    }

    private void reinitialiserSaisie() {
        saisie.setLength(0);
        ecran.setText("");
    }

    private void valider() {
        List<Part> choisies = partsSelectionnees();
        double du = totalSelection();
        double saisi = totalSaisi();
        if (choisies.isEmpty() && !parts.isEmpty()) {
            Ui.message(lblMessage, "Sélectionnez au moins un article.", false);
            return;
        }
        if (saisi + 0.005 < du) {
            Ui.message(lblMessage, "Il manque " + Format.euros(du - saisi) + ".", false);
            return;
        }
        if (du < 0.005 && saisi > 0) {
            Ui.message(lblMessage, "Rien à encaisser sur la sélection : supprimez les montants saisis.", false);
            return;
        }

        Map<Part, Double> montants = new LinkedHashMap<>();
        for (Part part : choisies) montants.put(part, part.montant());

        double rendu = 0;
        if (du >= 0.005) {
            Paiement p = new Paiement(cmd, du);
            saisis.forEach(p::ajouterMontant);
            try {
                p.finaliser();
            } catch (IllegalStateException ex) {
                Ui.message(lblMessage, ex.getMessage(), false);
                return;
            }
            montants.forEach((part, m) -> p.affecter(part.ligne, m));
            data.ajouterPaiement(p);
            rendu = p.getMontantRendu();
        }
        montants.forEach((part, m) -> part.ligne.payer(m));
        parts.removeAll(choisies);
        saisis.clear();
        reinitialiserSaisie();

        if (cmd.getToutesLignes().stream().allMatch(LigneCommande::estSoldee)) {
            ecouteur.cloture(cmd, rendu);
            return;
        }
        double resteCommande = cmd.getToutesLignes().stream().mapToDouble(LigneCommande::getResteAPayer).sum();
        Ui.message(lblMessage, "✓ Paiement de " + Format.euros(du) + " enregistré"
                + (rendu > 0.005 ? " — rendu " + Format.euros(rendu) : "")
                + ". Reste sur la table : " + Format.euros(resteCommande) + ".", true);
        reconstruireParts();
        majHistorique();
        majTotaux();
        ecouteur.paiementPartiel();
    }

    /** Évalue une expression simple (+ − × ÷, priorité des opérateurs). NaN si invalide. */
    static double evaluer(String expr) {
        try {
            String e = expr.replace('÷', '/').replace('×', '*').replace('−', '-').replace(',', '.');
            List<Double> valeurs = new ArrayList<>();
            List<Character> ops = new ArrayList<>();
            StringBuilder nombre = new StringBuilder();
            for (char c : e.toCharArray()) {
                if ("+-*/".indexOf(c) >= 0 && nombre.length() > 0) {
                    valeurs.add(Double.parseDouble(nombre.toString()));
                    ops.add(c);
                    nombre.setLength(0);
                } else {
                    nombre.append(c);
                }
            }
            valeurs.add(Double.parseDouble(nombre.toString()));
            for (int i = 0; i < ops.size(); ) {
                char op = ops.get(i);
                if (op == '*' || op == '/') {
                    double r = op == '*' ? valeurs.get(i) * valeurs.get(i + 1) : valeurs.get(i) / valeurs.get(i + 1);
                    valeurs.set(i, r);
                    valeurs.remove(i + 1);
                    ops.remove(i);
                } else {
                    i++;
                }
            }
            double r = valeurs.get(0);
            for (int i = 0; i < ops.size(); i++) r = ops.get(i) == '+' ? r + valeurs.get(i + 1) : r - valeurs.get(i + 1);
            return Double.isFinite(r) ? r : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }
}
