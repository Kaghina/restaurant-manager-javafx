package model;

import enums.StatutCommande;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Commande d'une table (ou à emporter), découpée en suites. */
public class Commande {
    private static int compteur = 1;

    private final int id;
    private final TableRestaurant table;
    private final List<Suite> suites = new ArrayList<>();
    private Suite suiteActive;
    private StatutCommande statut = StatutCommande.EN_COURS;
    private final LocalDateTime dateHeure;
    private LocalDateTime dateCloture;
    private int nombreCouverts;
    private boolean paiementAmorce;
    private String nomClient = "";

    public Commande(TableRestaurant table, int nombreCouverts) {
        this(table, nombreCouverts, LocalDateTime.now());
    }

    public Commande(TableRestaurant table, int nombreCouverts, LocalDateTime dateHeure) {
        this.id = compteur++;
        this.table = table;
        this.nombreCouverts = Math.max(1, nombreCouverts);
        this.dateHeure = dateHeure;
        this.suiteActive = new Suite(1);
        this.suites.add(suiteActive);
    }

    // ── Suites ───────────────────────────────────────────────

    public Suite ajouterNouvelleSuite() {
        Suite s = new Suite(suites.size() + 1);
        suites.add(s);
        suiteActive = s;
        return s;
    }

    /** Ajoute un article ; si la suite active est déjà partie en cuisine, une nouvelle suite est créée. */
    public LigneCommande ajouterPlat(Plat plat) {
        if (suiteActive.estEnvoyee()) ajouterNouvelleSuite();
        return suiteActive.ajouterPlat(plat, 1);
    }

    public void setSuiteActive(Suite s) {
        if (suites.contains(s) && !s.estEnvoyee()) suiteActive = s;
    }

    /** Envoie toutes les suites saisies : la suite 1 est lancée, les autres attendent la réclame. */
    public void envoyerEnCuisine() {
        for (Suite s : suites) {
            if (!s.estEnvoyee() && !s.estVide()) s.envoyer(s.getNumero() == 1);
        }
    }

    public Optional<Suite> prochaineSuiteAReclamer() {
        return suites.stream().filter(Suite::estEnAttente).min(Comparator.comparingInt(Suite::getNumero));
    }

    public boolean aDesSuitesAEnvoyer() {
        return suites.stream().anyMatch(s -> !s.estEnvoyee() && !s.estVide());
    }

    public boolean aDejaEnvoye() { return suites.stream().anyMatch(Suite::estEnvoyee); }

    /** Quantité d'un plat pas encore envoyée en cuisine (pour le contrôle de stock). */
    public int quantiteNonEnvoyee(Plat plat) {
        return suites.stream().filter(s -> !s.estEnvoyee()).mapToInt(s -> s.quantite(plat)).sum();
    }

    // ── Totaux ───────────────────────────────────────────────

    public List<LigneCommande> getToutesLignes() {
        List<LigneCommande> toutes = new ArrayList<>();
        for (Suite s : suites) toutes.addAll(s.getLignes());
        return toutes;
    }

    public double getTotal()     { return suites.stream().mapToDouble(Suite::getTotal).sum(); }

    public double getTotalBrut() {
        return getToutesLignes().stream().mapToDouble(LigneCommande::getSousTotalBrut).sum();
    }

    public boolean estVide() { return suites.stream().allMatch(Suite::estVide); }

    // ── Clôture ──────────────────────────────────────────────

    public void cloturer() { cloturer(LocalDateTime.now()); }

    public void cloturer(LocalDateTime date) {
        statut = StatutCommande.PAYEE;
        dateCloture = date;
    }

    /** Annule la clôture (table encaissée par erreur) : la commande redevient en cours, à l'addition. */
    public void rouvrir() {
        statut = StatutCommande.EN_COURS;
        dateCloture = null;
        paiementAmorce = true;
    }

    public boolean estAEmporter() { return table.getZone() == TableRestaurant.Zone.EMPORTER; }

    // ── Accesseurs ───────────────────────────────────────────

    public int getId()                    { return id; }
    public TableRestaurant getTable()     { return table; }
    public List<Suite> getSuites()        { return suites; }
    public Suite getSuiteActive()         { return suiteActive; }
    public StatutCommande getStatut()     { return statut; }
    public LocalDateTime getDateHeure()   { return dateHeure; }
    public LocalDateTime getDateCloture() { return dateCloture; }
    public int getNombreCouverts()        { return nombreCouverts; }
    public void setNombreCouverts(int n)  { if (n >= 1) nombreCouverts = n; }
    public boolean isPaiementAmorce()     { return paiementAmorce; }
    public void setPaiementAmorce(boolean b) { paiementAmorce = b; }
    public String getNomClient()          { return nomClient; }
    public void setNomClient(String n)    { nomClient = n == null ? "" : n.trim(); }
}
