package controller;

import enums.StatutReservation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import manager.RestaurantData;
import model.Client;
import model.Reservation;
import model.TableRestaurant;
import util.Format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Onglet Réservations : formulaire de réservation + planning du jour. */
class ReservationController {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE);

    private final RestaurantData data = RestaurantData.get();
    private final VBox liste = new VBox(8);
    private final Label titreListe = Ui.label("", "section-titre");
    private final DatePicker filtreDate = new DatePicker(LocalDate.now());

    Node getView() {
        VBox page = Ui.page("Réservations", "Enregistrez les réservations et suivez l'arrivée des clients.");

        VBox formulaire = construireFormulaire();
        formulaire.setPrefWidth(400);
        formulaire.setMinWidth(380);

        Button precedent = Ui.bouton("◀", "btn-secondary", e -> filtreDate.setValue(filtreDate.getValue().minusDays(1)));
        Button suivant = Ui.bouton("▶", "btn-secondary", e -> filtreDate.setValue(filtreDate.getValue().plusDays(1)));
        Button aujourdhui = Ui.bouton("Aujourd'hui", "btn-secondary", e -> filtreDate.setValue(LocalDate.now()));
        filtreDate.valueProperty().addListener((o, a, n) -> { if (n == null) filtreDate.setValue(LocalDate.now()); else majListe(); });
        HBox barre = new HBox(8, precedent, filtreDate, suivant, aujourdhui);
        barre.setAlignment(Pos.CENTER_LEFT);

        VBox planning = Ui.carte(titreListe, barre, liste);
        HBox.setHgrow(planning, Priority.ALWAYS);

        HBox colonnes = new HBox(16, formulaire, planning);
        page.getChildren().add(colonnes);
        majListe();
        return Ui.defilement(page);
    }

    // ═══ FORMULAIRE ═══════════════════════════════════════════

    private VBox construireFormulaire() {
        TextField tel = new TextField();
        tel.setPromptText("06 12 34 56 78");
        TextField nom = new TextField();
        nom.setPromptText("Nom *");
        TextField prenom = new TextField();
        prenom.setPromptText("Prénom *");
        TextField email = new TextField();
        email.setPromptText("Email");
        TextField notes = new TextField();
        notes.setPromptText("Allergies, préférences…");
        Label infoClient = new Label();
        infoClient.setWrapText(true);

        Button chercher = Ui.bouton("🔍", "btn-secondary", null);
        chercher.setTooltip(new Tooltip("Rechercher un client existant"));
        Runnable rechercher = () -> {
            Client c = data.trouverClientParTel(tel.getText());
            boolean trouve = c != null;
            if (trouve) {
                nom.setText(c.getNom());
                prenom.setText(c.getPrenom());
                email.setText(c.getEmail());
                notes.setText(c.getNotes());
                Ui.message(infoClient, "✓ " + c.getNomComplet() + " — " + c.getNombreVisites() + " visite(s)"
                        + (c.estFidele() ? " ⭐ client fidèle" : ""), true);
            } else if (!tel.getText().isBlank()) {
                Ui.message(infoClient, "Nouveau client : complétez ses coordonnées.", false);
            }
            for (TextField f : List.of(nom, prenom, email)) f.setDisable(trouve);
        };
        chercher.setOnAction(e -> rechercher.run());
        tel.setOnAction(e -> rechercher.run());
        tel.textProperty().addListener((o, a, n) -> {
            for (TextField f : List.of(nom, prenom, email)) f.setDisable(false);
            infoClient.setText("");
        });
        HBox telBox = new HBox(6, tel, chercher);
        HBox.setHgrow(tel, Priority.ALWAYS);

        DatePicker date = new DatePicker(LocalDate.now());
        Spinner<Integer> heure = new Spinner<>(11, 23, 20);
        Spinner<Integer> minute = new Spinner<>(0, 45, 0, 15);
        heure.setPrefWidth(80);
        minute.setPrefWidth(80);
        Spinner<Integer> personnes = new Spinner<>(1, 12, 2);
        personnes.setPrefWidth(90);
        personnes.setEditable(true);

        ComboBox<TableRestaurant> tableCombo = new ComboBox<>();
        tableCombo.setPromptText("Choisir une table");
        tableCombo.setMaxWidth(Double.MAX_VALUE);
        tableCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TableRestaurant t) { return t == null ? "" : t.toString(); }
            @Override public TableRestaurant fromString(String s) { return null; }
        });
        Label dispo = Ui.label("", "texte-aide");
        Runnable majTables = () -> {
            if (date.getValue() == null) return;
            LocalDateTime dt = LocalDateTime.of(date.getValue(), LocalTime.of(heure.getValue(), minute.getValue()));
            TableRestaurant avant = tableCombo.getValue();
            List<TableRestaurant> libres = data.getTablesSalle().stream()
                    .filter(t -> t.getCapacite() >= personnes.getValue())
                    .filter(t -> data.conflit(t, dt).isEmpty())
                    .sorted(Comparator.comparingInt(TableRestaurant::getCapacite).thenComparingInt(TableRestaurant::getNumero))
                    .collect(Collectors.toList());
            tableCombo.getItems().setAll(libres);
            tableCombo.setValue(libres.contains(avant) ? avant : libres.isEmpty() ? null : libres.get(0));
            dispo.setText(libres.isEmpty() ? "Aucune table disponible sur ce créneau : utilisez la liste d'attente."
                    : libres.size() + " table(s) disponible(s) sur ce créneau.");
        };
        date.valueProperty().addListener((o, a, n) -> majTables.run());
        heure.valueProperty().addListener((o, a, n) -> majTables.run());
        minute.valueProperty().addListener((o, a, n) -> majTables.run());
        personnes.valueProperty().addListener((o, a, n) -> majTables.run());
        majTables.run();

        CheckBox attente = new CheckBox("Mettre en liste d'attente");
        TextField commentaire = new TextField();
        commentaire.setPromptText("Occasion, demande particulière…");
        Label message = new Label();
        message.setWrapText(true);

        Button reserver = Ui.bouton("✔ Enregistrer la réservation", "btn-primary", e -> {
            String telephone = tel.getText().trim();
            TableRestaurant table = tableCombo.getValue();
            if (RestaurantData.normaliserTel(telephone).replace("+", "").length() < 10) {
                Ui.message(message, "⚠ Numéro de téléphone invalide (10 chiffres).", false); return;
            }
            if (nom.getText().isBlank() || prenom.getText().isBlank()) {
                Ui.message(message, "⚠ Nom et prénom obligatoires.", false); return;
            }
            if (date.getValue() == null) { Ui.message(message, "⚠ Date obligatoire.", false); return; }
            LocalDateTime dt = LocalDateTime.of(date.getValue(), LocalTime.of(heure.getValue(), minute.getValue()));
            if (dt.isBefore(LocalDateTime.now().minusMinutes(15))) {
                Ui.message(message, "⚠ Ce créneau est déjà passé.", false); return;
            }
            if (table == null) { Ui.message(message, "⚠ Choisissez une table disponible.", false); return; }

            Client client = data.trouverClientParTel(telephone);
            if (client == null) {
                client = new Client(nom.getText().trim(), prenom.getText().trim(), telephone, email.getText().trim());
                data.getClients().add(client);
            }
            client.setNotes(notes.getText().trim());

            Reservation r = new Reservation(client, table, dt, personnes.getValue());
            r.setCommentaire(commentaire.getText().trim());
            if (attente.isSelected()) r.changerStatut(StatutReservation.EN_ATTENTE);
            data.getReservations().add(r);

            Ui.message(message, "✓ Réservation enregistrée : " + client.getNomComplet() + ", " + table.getLibelle()
                    + " le " + dt.format(Format.DATE_HEURE) + ".", true);
            for (TextField f : List.of(tel, nom, prenom, email, notes, commentaire)) f.clear();
            attente.setSelected(false);
            filtreDate.setValue(date.getValue());
            majTables.run();
            majListe();
        });
        reserver.setMaxWidth(Double.MAX_VALUE);

        HBox horaire = new HBox(6, heure, Ui.label("h", "texte-muted"), minute);
        horaire.setAlignment(Pos.CENTER_LEFT);

        GridPane grille = new GridPane();
        grille.setHgap(10);
        grille.setVgap(8);
        int l = 0;
        grille.addRow(l++, Ui.label("Téléphone *", "champ-label"), telBox);
        grille.add(infoClient, 1, l++);
        grille.addRow(l++, Ui.label("Nom", "champ-label"), nom);
        grille.addRow(l++, Ui.label("Prénom", "champ-label"), prenom);
        grille.addRow(l++, Ui.label("Email", "champ-label"), email);
        grille.addRow(l++, Ui.label("Notes client", "champ-label"), notes);
        grille.addRow(l++, Ui.label("Date", "champ-label"), date);
        grille.addRow(l++, Ui.label("Heure", "champ-label"), horaire);
        grille.addRow(l++, Ui.label("Personnes", "champ-label"), personnes);
        grille.addRow(l++, Ui.label("Table", "champ-label"), tableCombo);
        grille.add(dispo, 1, l++);
        grille.addRow(l++, Ui.label("Commentaire", "champ-label"), commentaire);
        grille.add(attente, 1, l);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(95);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        grille.getColumnConstraints().addAll(c0, c1);

        return Ui.carte(Ui.label("Nouvelle réservation", "section-titre"), grille, reserver, message);
    }

    // ═══ PLANNING ═════════════════════════════════════════════

    private void majListe() {
        LocalDate jour = filtreDate.getValue();
        List<Reservation> duJour = data.getReservations().stream()
                .filter(r -> r.getDateHeure().toLocalDate().equals(jour))
                .sorted(Comparator.comparing(Reservation::getDateHeure))
                .collect(Collectors.toList());
        int couverts = duJour.stream().filter(Reservation::estActive).mapToInt(Reservation::getNombrePersonnes).sum();
        String jourTexte = jour.format(JOUR);
        titreListe.setText(Character.toUpperCase(jourTexte.charAt(0)) + jourTexte.substring(1)
                + "  ·  " + duJour.size() + " réservation(s), " + couverts + " couvert(s) attendus");

        liste.getChildren().clear();
        if (duJour.isEmpty()) {
            liste.getChildren().add(Ui.label("Aucune réservation ce jour.", "texte-vide"));
            return;
        }
        for (Reservation r : duJour) liste.getChildren().add(carteReservation(r));
    }

    private HBox carteReservation(Reservation r) {
        Label heure = Ui.label(Format.heure(r.getDateHeure()), "resa-heure");
        VBox infos = new VBox(2,
                Ui.label(r.getClient().getNomComplet() + (r.getClient().estFidele() ? "  ⭐" : ""), "resa-nom"),
                Ui.label("📞 " + r.getClient().getTelephone() + "   ·   " + r.getTable().getLibelle()
                        + "   ·   " + r.getNombrePersonnes() + " pers.", "texte-muted"));
        if (!r.getCommentaire().isEmpty()) infos.getChildren().add(Ui.label("📝 " + r.getCommentaire(), "texte-muted"));
        if (!r.getClient().getNotes().isEmpty()) infos.getChildren().add(Ui.label("⚠ " + r.getClient().getNotes(), "texte-alerte"));

        Label statut = Ui.label(r.getStatut().getLibelle(), switch (r.getStatut()) {
            case CONFIRMEE  -> "chip-primary";
            case EN_ATTENTE -> "chip-warning";
            case ARRIVEE    -> "chip-success";
            case NO_SHOW    -> "chip-danger";
            case ANNULEE    -> "chip";
        });

        ComboBox<StatutReservation> combo = new ComboBox<>();
        combo.getItems().addAll(StatutReservation.values());
        combo.setValue(r.getStatut());
        combo.setPrefWidth(150);
        combo.setOnAction(e -> {
            if (combo.getValue() != null && combo.getValue() != r.getStatut()) {
                r.changerStatut(combo.getValue());
                majListe();
            }
        });

        Button modifier = Ui.bouton("✏ Modifier", "btn-secondary-sm", e -> modifier(r));
        modifier.setDisable(!r.estActive());
        modifier.setTooltip(new Tooltip(r.estActive() ? "Modifier la date, l'heure, la table, le nombre de personnes…"
                : "Seules les réservations confirmées ou en liste d'attente sont modifiables"));

        HBox carte = new HBox(14, heure, infos, Ui.espace(), statut, combo, modifier);
        carte.setAlignment(Pos.CENTER_LEFT);
        carte.getStyleClass().add("resa-card");
        if (!r.estActive()) carte.getStyleClass().add("resa-inactive");
        return carte;
    }

    // ═══ MODIFICATION ═════════════════════════════════════════

    private void modifier(Reservation r) {
        Client client = r.getClient();
        DatePicker date = new DatePicker(r.getDateHeure().toLocalDate());
        Spinner<Integer> heure = new Spinner<>(11, 23, Math.max(11, r.getDateHeure().getHour()));
        Spinner<Integer> minute = new Spinner<>(0, 59, r.getDateHeure().getMinute(), 15);
        heure.setPrefWidth(80);
        minute.setPrefWidth(80);
        Spinner<Integer> personnes = new Spinner<>(1, 12, r.getNombrePersonnes());
        personnes.setEditable(true);
        personnes.setPrefWidth(90);

        ComboBox<TableRestaurant> tableCombo = new ComboBox<>();
        tableCombo.setMaxWidth(Double.MAX_VALUE);
        tableCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TableRestaurant t) { return t == null ? "" : t.toString(); }
            @Override public TableRestaurant fromString(String s) { return null; }
        });
        Label dispo = Ui.label("", "texte-aide");
        Runnable majTables = () -> {
            if (date.getValue() == null) return;
            LocalDateTime dt = LocalDateTime.of(date.getValue(), LocalTime.of(heure.getValue(), minute.getValue()));
            TableRestaurant avant = tableCombo.getValue() != null ? tableCombo.getValue() : r.getTable();
            List<TableRestaurant> libres = data.getTablesSalle().stream()
                    .filter(t -> t.getCapacite() >= personnes.getValue())
                    .filter(t -> data.conflit(t, dt, r).isEmpty())
                    .sorted(Comparator.comparingInt(TableRestaurant::getCapacite).thenComparingInt(TableRestaurant::getNumero))
                    .collect(Collectors.toList());
            tableCombo.getItems().setAll(libres);
            tableCombo.setValue(libres.contains(avant) ? avant : libres.isEmpty() ? null : libres.get(0));
            dispo.setText(libres.isEmpty() ? "Aucune table disponible sur ce créneau."
                    : libres.contains(avant) ? libres.size() + " table(s) disponible(s)."
                    : "⚠ " + avant.getLibelle() + " n'est plus possible : " + libres.get(0).getLibelle() + " proposée.");
        };
        date.valueProperty().addListener((o, a, n) -> majTables.run());
        heure.valueProperty().addListener((o, a, n) -> majTables.run());
        minute.valueProperty().addListener((o, a, n) -> majTables.run());
        personnes.valueProperty().addListener((o, a, n) -> majTables.run());
        majTables.run();

        TextField tel = new TextField(client.getTelephone());
        TextField email = new TextField(client.getEmail());
        TextField notes = new TextField(client.getNotes());
        notes.setPromptText("Allergies, préférences…");
        TextField commentaire = new TextField(r.getCommentaire());
        commentaire.setPromptText("Occasion, demande particulière…");
        Label erreur = new Label();
        erreur.setWrapText(true);

        HBox horaire = new HBox(6, heure, Ui.label("h", "texte-muted"), minute);
        horaire.setAlignment(Pos.CENTER_LEFT);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        int l = 0;
        g.addRow(l++, Ui.label("Client", "champ-label"), Ui.label(client.getNomComplet(), "resa-nom"));
        g.addRow(l++, Ui.label("Téléphone", "champ-label"), tel);
        g.addRow(l++, Ui.label("Email", "champ-label"), email);
        g.addRow(l++, Ui.label("Notes client", "champ-label"), notes);
        g.addRow(l++, Ui.label("Date", "champ-label"), date);
        g.addRow(l++, Ui.label("Heure", "champ-label"), horaire);
        g.addRow(l++, Ui.label("Personnes", "champ-label"), personnes);
        g.addRow(l++, Ui.label("Table", "champ-label"), tableCombo);
        g.add(dispo, 1, l++);
        g.addRow(l++, Ui.label("Commentaire", "champ-label"), commentaire);
        g.add(erreur, 1, l);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(95);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPrefWidth(320);
        g.getColumnConstraints().addAll(c0, c1);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Modifier la réservation");
        d.setHeaderText("Modifier la réservation de " + client.getNomComplet());
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Ui.styler(d);

        Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Enregistrer");
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String erreurTexte = null;
            if (RestaurantData.normaliserTel(tel.getText()).replace("+", "").length() < 10)
                erreurTexte = "⚠ Numéro de téléphone invalide (10 chiffres).";
            else if (date.getValue() == null)
                erreurTexte = "⚠ Date obligatoire.";
            else if (tableCombo.getValue() == null)
                erreurTexte = "⚠ Choisissez une table disponible.";
            else {
                Client autre = data.trouverClientParTel(tel.getText());
                if (autre != null && autre != client) erreurTexte = "⚠ Ce numéro appartient déjà à " + autre.getNomComplet() + ".";
            }
            if (erreurTexte != null) {
                Ui.message(erreur, erreurTexte, false);
                ev.consume();
            }
        });

        if (d.showAndWait().filter(b -> b == ButtonType.OK).isEmpty()) return;
        LocalDateTime dt = LocalDateTime.of(date.getValue(), LocalTime.of(heure.getValue(), minute.getValue()));
        r.setDateHeure(dt);
        r.setNombrePersonnes(personnes.getValue());
        r.setTable(tableCombo.getValue());
        r.setCommentaire(commentaire.getText().trim());
        client.setTelephone(tel.getText().trim());
        client.setEmail(email.getText().trim());
        client.setNotes(notes.getText().trim());
        if (filtreDate.getValue().equals(dt.toLocalDate())) majListe();
        else filtreDate.setValue(dt.toLocalDate());
    }
}
