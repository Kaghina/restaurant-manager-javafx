package controller;

import enums.CategoriePlat;
import enums.StatutTable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import manager.RestaurantData;
import model.*;
import model.TableRestaurant.Zone;
import service.TicketService;
import util.Format;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Onglet "Salle" : plan de salle, ouverture de table, prise de commande, encaissement. */
public class SalleController {

    private static final double LARGEUR_TUILE = 140;
    private static final double TABLE_L = 92;
    private static final double TABLE_H = 72;

    private final RestaurantData data = RestaurantData.get();
    private final BorderPane root = new BorderPane();
    private final StackPane panneauDroit = new StackPane();

    private TableRestaurant tableSelectionnee;
    /** Vue de droite à reconstruire lors d'un rafraîchissement (null pendant un encaissement). */
    private Runnable vueCourante;
    /** Onglet de carte actif, conservé entre deux rafraîchissements. */
    private int ongletMenu;

    public BorderPane getView() {
        panneauDroit.getStyleClass().add("panneau-droit");
        root.setCenter(panneauDroit);
        rafraichirPlan();
        afficherAccueil();
        return root;
    }

    /** Appelé lorsqu'on revient sur l'onglet (réservations ou stocks ont pu changer). */
    public void rafraichir() {
        rafraichirPlan();
        if (vueCourante != null) vueCourante.run();
    }

    /** Rafraîchit uniquement le plan (durées d'occupation), sans toucher à la saisie en cours. */
    public void rafraichirPlanSeul() {
        rafraichirPlan();
    }

    // ═══ PLAN DE SALLE ════════════════════════════════════════

    private void rafraichirPlan() {
        VBox plan = new VBox(14);
        plan.setPadding(new Insets(18));
        plan.getStyleClass().add("plan-container");

        long occupees = data.getTablesSalle().stream().filter(TableRestaurant::estOccupee).count();
        HBox entete = new HBox(10,
                Ui.label("Plan de salle", "plan-titre"), Ui.espace(),
                Ui.label(occupees + " / " + data.getTablesSalle().size() + " tables occupées", "chip"));
        entete.setAlignment(Pos.CENTER_LEFT);

        FlowPane legende = new FlowPane(12, 6);
        for (StatutTable s : StatutTable.values()) legende.getChildren().add(legendeItem(s));

        plan.getChildren().addAll(entete, legende);
        for (Zone zone : Zone.values()) plan.getChildren().add(construireZone(zone));

        ScrollPane sp = Ui.defilement(plan);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefWidth(472);
        sp.setMinWidth(462);
        sp.getStyleClass().add("plan-scroll");
        root.setLeft(sp);
    }

    private VBox construireZone(Zone zone) {
        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(10);
        for (TableRestaurant t : data.getTablesZone(zone)) grille.add(boutonTable(t), t.getCol(), t.getRow());
        VBox box = new VBox(8, Ui.label(zone.getLibelle(), "zone-label"), grille);
        box.getStyleClass().add("zone-box");
        return box;
    }

    private VBox boutonTable(TableRestaurant table) {
        StatutTable statut = data.statutAffiche(table);
        VBox btn = new VBox(1);
        btn.setAlignment(Pos.CENTER);
        btn.setPrefSize(TABLE_L, TABLE_H);
        btn.setMinSize(TABLE_L, TABLE_H);
        btn.setCursor(Cursor.HAND);
        btn.getStyleClass().addAll("table-btn", statut.getStyleClass());
        if (table == tableSelectionnee) btn.getStyleClass().add("table-selected");

        btn.getChildren().add(Ui.label(table.getLibelle(), "table-num"));
        Commande cmd = table.getCommandeEnCours();
        if (cmd != null) {
            btn.getChildren().add(Ui.label(Format.euros(cmd.getTotal()), "table-stat"));
            long minutes = Duration.between(cmd.getDateHeure(), LocalDateTime.now()).toMinutes();
            String detail = table.getZone() == Zone.EMPORTER
                    ? (cmd.getNomClient().isEmpty() ? minutes + " min" : cmd.getNomClient())
                    : cmd.getNombreCouverts() + " couv. · " + minutes + " min";
            btn.getChildren().add(Ui.label(detail, "table-cap"));
        } else if (statut == StatutTable.NETTOYAGE) {
            btn.getChildren().add(Ui.label("à débarrasser", "table-cap"));
        } else {
            btn.getChildren().add(Ui.label(table.getZone() == Zone.EMPORTER ? "libre" : table.getCapacite() + " pers.", "table-cap"));
            data.prochaineReservation(table).ifPresent(r ->
                    btn.getChildren().add(Ui.label("📅 " + Format.heure(r.getDateHeure()), "table-stat")));
        }
        Tooltip.install(btn, new Tooltip(table.getLibelle() + " — " + statut.getLibelle()));
        btn.setOnMouseClicked(e -> selectionnerTable(table));
        return btn;
    }

    private HBox legendeItem(StatutTable statut) {
        Region carre = new Region();
        carre.getStyleClass().addAll("legende-carre", statut.getStyleClass());
        HBox hb = new HBox(5, carre, Ui.label(statut.getLibelle(), "legende-texte"));
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private void selectionnerTable(TableRestaurant table) {
        tableSelectionnee = table;
        rafraichirPlan();
        if (table.estOccupee())                           afficherCommande(table);
        else if (table.getStatut() == StatutTable.NETTOYAGE) afficherNettoyage(table);
        else                                               afficherOuverture(table);
    }

    private void retourAccueil() {
        tableSelectionnee = null;
        rafraichirPlan();
        afficherAccueil();
    }

    // ═══ VUES SIMPLES ═════════════════════════════════════════

    private void afficherAccueil() {
        vueCourante = this::afficherAccueil;
        VBox box = new VBox(10,
                Ui.label("🍽", "accueil-icone"),
                Ui.label("Sélectionnez une table", "accueil-titre"),
                Ui.label("Cliquez sur une table du plan pour l'ouvrir, prendre la commande ou encaisser.", "accueil-texte"));
        box.setAlignment(Pos.CENTER);
        setDroit(box, Pos.CENTER);
    }

    private void afficherNettoyage(TableRestaurant table) {
        vueCourante = () -> afficherNettoyage(table);
        Button pret = Ui.bouton("✔ Table débarrassée et prête", "btn-primary", e -> {
            table.marquerPropre();
            retourAccueil();
        });
        VBox carte = Ui.carte(
                Ui.label(table.getLibelle() + " — à débarrasser", "panneau-titre"),
                Ui.label("Le service est terminé. Marquez la table comme prête pour l'attribuer à de nouveaux clients.", "texte-muted"),
                pret);
        carte.setMaxWidth(440);
        setDroit(carte, Pos.TOP_LEFT);
    }

    private void afficherOuverture(TableRestaurant table) {
        vueCourante = () -> afficherOuverture(table);
        boolean emporter = table.getZone() == Zone.EMPORTER;
        VBox carte = Ui.carte();
        carte.setMaxWidth(460);
        carte.getChildren().add(Ui.label(emporter ? "Nouvelle commande à emporter (" + table.getLibelle() + ")"
                : "Ouvrir la table " + table.getLibelle(), "panneau-titre"));

        Optional<Reservation> resa = emporter ? Optional.empty() : data.prochaineReservation(table);
        CheckBox estResa = new CheckBox();
        resa.ifPresent(r -> {
            Label info = Ui.label("📅 Réservée à " + Format.heure(r.getDateHeure()) + " — " + r.getClient().getNomComplet()
                    + " (" + r.getNombrePersonnes() + " pers.)"
                    + (r.getCommentaire().isEmpty() ? "" : "\n📝 " + r.getCommentaire())
                    + (r.getClient().getNotes().isEmpty() ? "" : "\n⚠ " + r.getClient().getNotes()), "encart-info");
            info.setWrapText(true);
            info.setMaxWidth(Double.MAX_VALUE);
            boolean proche = r.getDateHeure().isBefore(LocalDateTime.now().plusMinutes(45));
            estResa.setText("Ce sont les clients de la réservation");
            estResa.setSelected(proche);
            carte.getChildren().addAll(info, estResa);
        });

        TextField nomClient = new TextField();
        Spinner<Integer> couverts = new Spinner<>(1, Math.max(1, table.getCapacite()),
                resa.map(Reservation::getNombrePersonnes).orElse(Math.min(2, table.getCapacite())));
        couverts.setEditable(true);
        couverts.setPrefWidth(110);
        if (emporter) {
            nomClient.setPromptText("Nom du client (facultatif)");
            carte.getChildren().addAll(Ui.label("Client", "champ-label"), nomClient);
        } else {
            carte.getChildren().addAll(
                    Ui.label("Capacité : " + table.getCapacite() + " personnes", "texte-muted"),
                    Ui.label("Nombre de couverts", "champ-label"), couverts);
        }

        Button ouvrir = Ui.bouton(emporter ? "✔ Démarrer la commande" : "✔ Ouvrir et prendre la commande", "btn-primary", e -> {
            Commande cmd = new Commande(table, emporter ? 1 : couverts.getValue());
            if (emporter) cmd.setNomClient(nomClient.getText());
            if (resa.isPresent() && estResa.isSelected()) {
                resa.get().marquerArrivee();
                cmd.setNomClient(resa.get().getClient().getNomComplet());
            }
            table.occuper(cmd);
            rafraichirPlan();
            afficherCommande(table);
        });
        ouvrir.setMaxWidth(Double.MAX_VALUE);
        ouvrir.setDefaultButton(true);
        carte.getChildren().add(ouvrir);
        setDroit(carte, Pos.TOP_LEFT);
    }

    // ═══ PRISE DE COMMANDE ════════════════════════════════════

    private void afficherCommande(TableRestaurant table) {
        Commande cmd = table.getCommandeEnCours();
        if (cmd == null) { retourAccueil(); return; }
        vueCourante = () -> afficherCommande(table);

        // En-tête
        String titre = table.getZone() == Zone.EMPORTER
                ? "À emporter " + table.getLibelle() + (cmd.getNomClient().isEmpty() ? "" : " — " + cmd.getNomClient())
                : "Table " + table.getLibelle().substring(1) + " · " + cmd.getNombreCouverts() + " couvert(s)";
        Button retour = Ui.bouton("← Plan", "btn-secondary", e -> retourAccueil());
        retour.setMinWidth(Region.USE_PREF_SIZE);
        Button liberer = Ui.bouton("🗑 Annuler", "btn-ghost-danger", e -> {
            if (Ui.confirmer("Annuler", "Annuler l'ouverture de " + table.getLibelle() + " ? Les articles saisis seront perdus.")) {
                table.annulerOuverture();
                retourAccueil();
            }
        });
        liberer.setVisible(!cmd.aDejaEnvoye() && !cmd.isPaiementAmorce());
        liberer.setManaged(liberer.isVisible());
        Button encaisser = Ui.bouton("💳 Encaisser " + Format.euros(cmd.getTotal()), "btn-success", e -> afficherEncaissement(table));
        encaisser.setDisable(cmd.estVide());
        encaisser.setMinWidth(Region.USE_PREF_SIZE);
        liberer.setMinWidth(Region.USE_PREF_SIZE);
        liberer.setTooltip(new Tooltip("Annuler l'ouverture de la table (rien n'a été envoyé en cuisine)"));

        VBox titreBox = new VBox(0, Ui.label(titre, "panneau-titre"),
                Ui.label("Ouverte à " + Format.heure(cmd.getDateHeure()) + " · " + table.getStatut().getLibelle(), "texte-muted"));
        titreBox.setMinWidth(0);
        HBox header = new HBox(12, retour, titreBox, Ui.espace(), liberer, encaisser);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 12, 0));

        // Carte (menu)
        TabPane menu = new TabPane();
        menu.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        menu.getStyleClass().add("menu-tabs");
        for (CategoriePlat cat : new CategoriePlat[]{CategoriePlat.ENTREE, CategoriePlat.PLAT, CategoriePlat.DESSERT}) {
            FlowPane fp = grilleArticles();
            for (Plat p : data.getMenuCategorie(cat)) fp.getChildren().add(tuilePlat(p, cmd, table));
            menu.getTabs().add(new Tab(cat.getLibelle(), Ui.defilement(fp)));
        }
        menu.getTabs().add(new Tab(CategoriePlat.BOISSON.getLibelle(), ongletsBoissons(cmd, table)));
        if (ongletMenu < menu.getTabs().size()) menu.getSelectionModel().select(ongletMenu);
        menu.getSelectionModel().selectedIndexProperty().addListener((o, a, n) -> ongletMenu = n.intValue());

        VBox recap = recapitulatif(cmd, table);
        recap.setPrefWidth(320);
        recap.setMinWidth(285);
        HBox centre = new HBox(14, menu, recap);
        HBox.setHgrow(menu, Priority.ALWAYS);

        BorderPane panneau = new BorderPane();
        panneau.setPadding(new Insets(16));
        panneau.setTop(header);
        panneau.setCenter(centre);
        setDroit(panneau, Pos.TOP_LEFT);
    }

    private FlowPane grilleArticles() {
        FlowPane fp = new FlowPane(10, 10);
        fp.setPadding(new Insets(12));
        return fp;
    }

    private TabPane ongletsBoissons(Commande cmd, TableRestaurant table) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("boisson-tabs");
        for (Map.Entry<String, String> sc : RestaurantData.SOUS_CATEGORIES_BOISSON.entrySet()) {
            List<Plat> plats = data.getMenuSousCategorie(sc.getKey());
            if (plats.isEmpty()) continue;
            FlowPane fp = grilleArticles();
            for (Plat p : plats) fp.getChildren().add(p.hasBouteille() ? tuileVin(p, cmd, table) : tuilePlat(p, cmd, table));
            tabs.getTabs().add(new Tab(sc.getValue(), Ui.defilement(fp)));
        }
        return tabs;
    }

    private Button tuilePlat(Plat plat, Commande cmd, TableRestaurant table) {
        Label nom = Ui.label(plat.getNom(), "tuile-nom");
        nom.setWrapText(true);
        nom.setPrefWidth(LARGEUR_TUILE - 22);
        nom.setMaxWidth(LARGEUR_TUILE - 22);
        VBox contenu = new VBox(3, nom, Ui.label(Format.euros(plat.getPrix()), "tuile-prix"));
        String statut = plat.getStatutAffichage();
        if (!statut.isEmpty()) contenu.getChildren().add(Ui.label(statut, plat.estDisponible() ? "badge-warning" : "badge-danger"));
        Button btn = new Button();
        btn.setGraphic(contenu);
        btn.getStyleClass().add("tuile-plat");
        btn.setPrefSize(LARGEUR_TUILE, 86);
        btn.setMinSize(LARGEUR_TUILE, 86);
        btn.setMaxSize(LARGEUR_TUILE, 86);
        btn.setDisable(!plat.estDisponible());
        btn.setOnAction(e -> ajouterArticle(plat, cmd, table));
        return btn;
    }

    private VBox tuileVin(Plat vin, Commande cmd, TableRestaurant table) {
        Circle pastille = new Circle(6, switch (vin.getCouleurVin()) {
            case "rouge" -> Color.web("#9b1c31");
            case "blanc" -> Color.web("#f3e3a1");
            default      -> Color.web("#f9a8b8");
        });
        pastille.setStroke(Color.web("#d1d5db"));
        Label nom = Ui.label(vin.getNom(), "tuile-nom");
        nom.setWrapText(true);
        nom.setPrefWidth(170);
        nom.setMaxWidth(170);
        HBox titre = new HBox(6, pastille, nom);
        titre.setAlignment(Pos.CENTER_LEFT);

        Button verre = Ui.bouton("🍷 Verre\n" + Format.euros(vin.getPrix()), "btn-verre", e -> ajouterArticle(vin, cmd, table));
        Button bouteille = Ui.bouton("🍾 Bouteille\n" + Format.euros(vin.getPrixBouteille()), "btn-bouteille",
                e -> ajouterArticle(vin.getVarianteBouteille(), cmd, table));
        HBox boutons = new HBox(6, verre, bouteille);
        VBox tuile = new VBox(8, titre, boutons);
        tuile.getStyleClass().add("tuile-vin");
        tuile.setMaxHeight(Region.USE_PREF_SIZE);
        tuile.setDisable(!vin.estDisponible());
        if (!vin.estDisponible()) tuile.getChildren().add(Ui.label("Rupture", "badge-danger"));
        return tuile;
    }

    private void ajouterArticle(Plat plat, Commande cmd, TableRestaurant table) {
        int dejaSaisi = cmd.quantiteNonEnvoyee(plat);
        if (!plat.peutServir(dejaSaisi + 1)) {
            Ui.erreur("Stock insuffisant", "Plus assez de stock pour ajouter « " + plat.getNom() + " ».");
            return;
        }
        cmd.ajouterPlat(plat);
        afficherCommande(table);
        rafraichirPlan();
    }

    // ═══ RÉCAPITULATIF ════════════════════════════════════════

    private VBox recapitulatif(Commande cmd, TableRestaurant table) {
        VBox lignes = new VBox(6);
        lignes.setPadding(new Insets(2));
        boolean plusieurs = cmd.getSuites().size() > 1;

        for (Suite suite : cmd.getSuites()) {
            if (plusieurs || suite.estEnvoyee()) lignes.getChildren().add(enteteSuite(cmd, suite, table));
            if (suite.estVide()) {
                lignes.getChildren().add(Ui.label("Vide — ajoutez des articles depuis la carte", "texte-vide"));
            } else {
                for (LigneCommande l : suite.getLignes()) lignes.getChildren().add(ligneRecap(l, suite, table));
            }
        }
        ScrollPane sp = Ui.defilement(lignes);
        VBox.setVgrow(sp, Priority.ALWAYS);

        Suite active = cmd.getSuiteActive();
        Button nouvelleSuite = Ui.bouton("➕ Nouvelle suite", "btn-purple", e -> {
            cmd.ajouterNouvelleSuite();
            afficherCommande(table);
        });
        nouvelleSuite.setMaxWidth(Double.MAX_VALUE);
        nouvelleSuite.setDisable(active.estVide() && !active.estEnvoyee());
        nouvelleSuite.setTooltip(new Tooltip("Créer un nouveau service (ex : les plats après les entrées)"));

        Button envoyer = Ui.bouton("📨 Envoyer en cuisine", "btn-warning", e -> {
            cmd.envoyerEnCuisine();
            table.majStatut();
            afficherCommande(table);
            rafraichirPlan();
        });
        envoyer.setMaxWidth(Double.MAX_VALUE);
        envoyer.setDisable(!cmd.aDesSuitesAEnvoyer());

        VBox actions = new VBox(8, envoyer, nouvelleSuite);

        VBox box = new VBox(10, new HBox(8, Ui.label("Commande", "recap-titre"), Ui.espace(),
                Ui.label(cmd.getToutesLignes().stream().mapToInt(LigneCommande::getQuantite).sum() + " article(s)", "chip")),
                sp, actions);

        cmd.prochaineSuiteAReclamer().ifPresent(s -> {
            Button reclamer = Ui.bouton("📣 Réclamer la suite " + s.getNumero(), "btn-reclamer", e -> {
                s.reclamer();
                table.majStatut();
                afficherCommande(table);
                rafraichirPlan();
            });
            reclamer.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(reclamer);
        });

        if (plusieurs) box.getChildren().add(Ui.label("La suite 1 part directement en cuisine, les suivantes attendent d'être réclamées.", "texte-aide"));

        HBox total = new HBox(Ui.label("Total", "recap-total-label"), Ui.espace(), Ui.label(Format.euros(cmd.getTotal()), "recap-total"));
        total.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(new Separator(), total);
        box.getStyleClass().add("recap-box");
        return box;
    }

    private HBox enteteSuite(Commande cmd, Suite suite, TableRestaurant table) {
        String texte;
        String style;
        if (suite.estLancee())          { texte = "en cuisine";        style = "chip-warning"; }
        else if (suite.estEnAttente())  { texte = "à réclamer";        style = "chip-purple"; }
        else if (suite == cmd.getSuiteActive()) { texte = "● en saisie"; style = "chip-primary"; }
        else                            { texte = "brouillon";         style = "chip"; }
        HBox h = new HBox(8, Ui.label("Suite " + suite.getNumero(), "suite-titre"), Ui.label(texte, style), Ui.espace());
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("suite-entete");
        if (!suite.estEnvoyee() && suite != cmd.getSuiteActive()) {
            h.getChildren().add(Ui.bouton("Saisir ici", "btn-lien", e -> {
                cmd.setSuiteActive(suite);
                afficherCommande(table);
            }));
        }
        if (suite.estEnvoyee()) {
            h.getChildren().add(Ui.bouton("📄 Bon", "btn-lien", e -> {
                TextArea bon = new TextArea(TicketService.bonCuisine(cmd, suite));
                bon.setEditable(false);
                bon.getStyleClass().add("ticket-area");
                bon.setPrefRowCount(14);
                Dialog<ButtonType> d = new Dialog<>();
                d.setTitle("Bon de cuisine");
                d.getDialogPane().setContent(bon);
                d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                Ui.styler(d);
                d.showAndWait();
            }));
        }
        return h;
    }

    private VBox ligneRecap(LigneCommande l, Suite suite, TableRestaurant table) {
        boolean modifiable = !suite.estEnvoyee() && l.getMontantPaye() < 0.005;
        Label nom = Ui.label(l.getPlat().getNom(), suite.estEnvoyee() ? "ligne-nom-envoyee" : "ligne-nom");
        nom.setWrapText(true);
        nom.setMaxWidth(150);
        HBox row = new HBox(6, Ui.label("×" + l.getQuantite(), "quantite-label"), nom, Ui.espace(),
                Ui.label(Format.euros(l.getSousTotal()), "ligne-prix"));
        row.setAlignment(Pos.CENTER_LEFT);
        if (modifiable) {
            Button moins = Ui.bouton("−", "btn-mini", e -> {
                if (l.getQuantite() > 1) l.setQuantite(l.getQuantite() - 1);
                else suite.retirerLigne(l);
                afficherCommande(table);
                rafraichirPlan();
            });
            Button plus = Ui.bouton("+", "btn-mini", e -> {
                if (!l.getPlat().peutServir(table.getCommandeEnCours().quantiteNonEnvoyee(l.getPlat()) + 1)) {
                    Ui.erreur("Stock insuffisant", "Plus assez de stock pour « " + l.getPlat().getNom() + " ».");
                    return;
                }
                l.setQuantite(l.getQuantite() + 1);
                afficherCommande(table);
                rafraichirPlan();
            });
            Button detail = Ui.bouton("✏", "btn-mini-accent", e -> afficherDetailLigne(l, table));
            detail.setTooltip(new Tooltip("Suppléments, retraits, commentaire"));
            row.getChildren().addAll(moins, plus, detail);
        }

        VBox cellule = new VBox(3, row);
        cellule.getStyleClass().add("ligne-recap");
        if (!l.getSupplements().isEmpty() || !l.getIngredientsRetires().isEmpty()) {
            FlowPane tags = new FlowPane(4, 4);
            tags.setPadding(new Insets(0, 0, 0, 30));
            l.getSupplements().forEach((n, p) -> tags.getChildren().add(
                    Ui.label("+ " + n + (p > 0 ? " " + Format.euros(p) : ""), "tag-supplement")));
            l.getIngredientsRetires().forEach(r -> tags.getChildren().add(Ui.label("sans " + r, "tag-retrait")));
            cellule.getChildren().add(tags);
        }
        if (!l.getNote().isBlank()) {
            Label note = Ui.label("💬 " + l.getNote(), "ligne-note");
            note.setWrapText(true);
            cellule.getChildren().add(note);
        }
        return cellule;
    }

    // ═══ DÉTAIL D'UNE LIGNE ═══════════════════════════════════

    private void afficherDetailLigne(LigneCommande ligne, TableRestaurant table) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Personnaliser — " + ligne.getPlat().getNom());
        dialog.setHeaderText(ligne.getPlat().getNom() + "  ×" + ligne.getQuantite());

        VBox suppList = new VBox(4);
        Runnable[] majSupp = new Runnable[1];
        majSupp[0] = () -> {
            suppList.getChildren().clear();
            ligne.getSupplements().forEach((nom, prix) -> {
                HBox r = new HBox(8, Ui.label("+ " + nom, "ligne-nom"), Ui.espace(), Ui.label(Format.euros(prix), "ligne-prix"),
                        Ui.bouton("✕", "btn-mini-danger", ev -> { ligne.retirerSupplement(nom); majSupp[0].run(); }));
                r.setAlignment(Pos.CENTER_LEFT);
                suppList.getChildren().add(r);
            });
        };
        majSupp[0].run();

        FlowPane predefinis = new FlowPane(6, 6);
        RestaurantData.SUPPLEMENTS.forEach((nom, prix) -> predefinis.getChildren().add(
                Ui.bouton(nom + "  +" + Format.euros(prix), "btn-supplement",
                        ev -> { ligne.ajouterSupplement(nom, prix); majSupp[0].run(); })));

        TextField suppNom = new TextField();
        suppNom.setPromptText("Autre supplément");
        TextField suppPrix = new TextField();
        suppPrix.setPromptText("Prix €");
        suppPrix.setPrefWidth(80);
        Label erreurSupp = new Label();
        Button ajoutSupp = Ui.bouton("Ajouter", "btn-secondary", ev -> {
            String nom = suppNom.getText().trim();
            double prix = suppPrix.getText().isBlank() ? 0 : Format.lireNombre(suppPrix.getText());
            if (nom.isEmpty() || Double.isNaN(prix) || prix < 0) {
                Ui.message(erreurSupp, "Nom ou prix invalide", false);
                return;
            }
            ligne.ajouterSupplement(nom, prix);
            suppNom.clear();
            suppPrix.clear();
            erreurSupp.setText("");
            majSupp[0].run();
        });
        HBox saisieSupp = new HBox(8, suppNom, suppPrix, ajoutSupp);
        saisieSupp.setAlignment(Pos.CENTER_LEFT);

        FlowPane retraits = new FlowPane(6, 6);
        Runnable[] majRetr = new Runnable[1];
        majRetr[0] = () -> {
            retraits.getChildren().clear();
            for (String r : ligne.getIngredientsRetires())
                retraits.getChildren().add(Ui.bouton("sans " + r + "  ✕", "tag-retrait-btn",
                        ev -> { ligne.retirerIngredientRetire(r); majRetr[0].run(); }));
        };
        majRetr[0].run();
        TextField retrait = new TextField();
        retrait.setPromptText("ex : oignon, sauce…");
        Runnable ajouterRetrait = () -> {
            String n = retrait.getText().trim();
            if (!n.isEmpty()) { ligne.ajouterIngredientRetire(n); retrait.clear(); majRetr[0].run(); }
        };
        retrait.setOnAction(ev -> ajouterRetrait.run());
        HBox saisieRetrait = new HBox(8, retrait, Ui.bouton("Retirer", "btn-secondary", ev -> ajouterRetrait.run()));

        TextArea commentaire = new TextArea(ligne.getNote());
        commentaire.setPromptText("Cuisson, allergies, instructions…");
        commentaire.setPrefRowCount(2);
        commentaire.setWrapText(true);
        commentaire.textProperty().addListener((o, a, n) -> ligne.setNote(n));

        VBox contenu = new VBox(10,
                Ui.label("➕ Suppléments", "section-lbl"), suppList, predefinis, saisieSupp, erreurSupp,
                new Separator(),
                Ui.label("✗ Ingrédients retirés", "section-lbl"), retraits, saisieRetrait,
                new Separator(),
                Ui.label("💬 Commentaire pour la cuisine", "section-lbl"), commentaire);
        contenu.setPadding(new Insets(4));
        contenu.setPrefWidth(520);
        ScrollPane sp = Ui.defilement(contenu);
        sp.setPrefHeight(520);
        dialog.getDialogPane().setContent(sp);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        Ui.styler(dialog);
        dialog.showAndWait();
        afficherCommande(table);
        rafraichirPlan();
    }

    // ═══ ENCAISSEMENT ═════════════════════════════════════════

    private void afficherEncaissement(TableRestaurant table) {
        Commande cmd = table.getCommandeEnCours();
        if (cmd == null) return;
        cmd.setPaiementAmorce(true);
        table.majStatut();
        rafraichirPlan();
        vueCourante = null; // ne pas perdre la saisie en cours lors d'un rafraîchissement
        EncaissementController enc = new EncaissementController(table, new EncaissementController.Ecouteur() {
            @Override public void retour() {
                afficherCommande(table);
                rafraichirPlan();
            }
            @Override public void cloture(Commande c, double rendu) {
                data.cloturer(c);
                tableSelectionnee = null;
                rafraichirPlan();
                afficherTicket(c, rendu);
            }
            @Override public void paiementPartiel() { rafraichirPlan(); }
        });
        setDroit(enc.getView(), Pos.TOP_LEFT);
    }

    private void afficherTicket(Commande cmd, double rendu) {
        vueCourante = this::afficherAccueil;
        String texte = data.ticket(cmd);
        TextArea ticket = new TextArea(texte);
        ticket.setEditable(false);
        ticket.getStyleClass().add("ticket-area");
        ticket.setPrefRowCount(24);
        ticket.setPrefColumnCount(42);

        VBox gauche = new VBox(12, Ui.label("✅ " + cmd.getTable().getLibelle() + " encaissée et clôturée", "succes-titre"));
        if (rendu > 0.005) gauche.getChildren().add(Ui.label("💶 Rendu monnaie : " + Format.euros(rendu), "rendu-monnaie"));
        Button imprimer = Ui.bouton("🖨 Imprimer", "btn-secondary", e -> imprimer(texte));
        Button terminer = Ui.bouton("✔ Terminer", "btn-primary", e -> retourAccueil());
        terminer.setDefaultButton(true);
        Button corriger = Ui.bouton("✏ Corriger le paiement", "btn-secondary", e -> {
            if (CorrectionPaiementDialog.ouvrir(data.paiementsDe(cmd))) afficherTicket(cmd, 0);
        });
        Button rouvrir = Ui.bouton("↩ Rouvrir la table", "btn-ghost-danger", e -> rouvrirCommande(cmd));
        rouvrir.setTooltip(new Tooltip("Erreur d'encaissement : annule les paiements et remet la commande sur la table"));
        gauche.getChildren().addAll(new HBox(10, imprimer, terminer), new HBox(10, corriger, rouvrir));

        VBox carte = Ui.carte(gauche, ticket);
        carte.setMaxWidth(520);
        setDroit(Ui.defilement(carte), Pos.TOP_LEFT);
    }

    private void rouvrirCommande(Commande cmd) {
        TableRestaurant table = cmd.getTable();
        if (!Ui.confirmer("Rouvrir la table", "Rouvrir " + table.getLibelle() + " ? Les paiements de cette commande ("
                + Format.euros(cmd.getTotal()) + ") seront annulés et la commande reviendra sur la table.")) return;
        try {
            data.rouvrir(cmd);
        } catch (IllegalStateException ex) {
            Ui.erreur("Rouvrir la table", ex.getMessage());
            return;
        }
        tableSelectionnee = table;
        rafraichirPlan();
        afficherCommande(table);
    }

    static void imprimer(String texte) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Ui.erreur("Impression", "Aucune imprimante disponible.");
            return;
        }
        if (job.showPrintDialog(null)) {
            Text t = new Text(texte);
            t.setFont(Font.font("Consolas", 10));
            if (job.printPage(t)) job.endJob();
            else Ui.erreur("Impression", "L'impression a échoué.");
        }
    }

    // ═══ UTILITAIRES ══════════════════════════════════════════

    private void setDroit(Node node, Pos position) {
        boolean pleinePage = node instanceof BorderPane || node instanceof ScrollPane;
        if (!pleinePage && node instanceof Region r) r.setMaxHeight(Region.USE_PREF_SIZE);
        panneauDroit.getChildren().setAll(node);
        StackPane.setAlignment(node, position);
        StackPane.setMargin(node, pleinePage ? Insets.EMPTY : new Insets(24));
    }
}
