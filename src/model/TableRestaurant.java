package model;

import enums.StatutTable;

/** Table du plan de salle (ou emplacement "à emporter"). */
public class TableRestaurant {

    public enum Zone {
        SALLE("🏠 Salle"), TERRASSE("🌿 Terrasse"), BAR("🍸 Bar"), EMPORTER("🛍 À emporter");

        private final String libelle;
        Zone(String libelle) { this.libelle = libelle; }
        public String getLibelle() { return libelle; }
        @Override public String toString() { return libelle; }
    }

    private final int numero;
    private final int capacite;
    private final Zone zone;
    private final int col;
    private final int row;
    private StatutTable statut = StatutTable.LIBRE;
    private Commande commandeEnCours;

    public TableRestaurant(int numero, int capacite, Zone zone, int col, int row) {
        this.numero = numero;
        this.capacite = capacite;
        this.zone = zone;
        this.col = col;
        this.row = row;
    }

    public void occuper(Commande commande) {
        if (commandeEnCours != null) throw new IllegalStateException("Table déjà occupée");
        commandeEnCours = commande;
        majStatut();
    }

    /** Fin de service : la table doit être débarrassée (sauf vente à emporter). */
    public void liberer() {
        commandeEnCours = null;
        statut = zone == Zone.EMPORTER ? StatutTable.LIBRE : StatutTable.NETTOYAGE;
    }

    /** Annule une table ouverte par erreur (aucun article envoyé). */
    public void annulerOuverture() {
        commandeEnCours = null;
        statut = StatutTable.LIBRE;
    }

    /** Remet sur la table une commande clôturée par erreur. */
    public void rouvrir(Commande commande) {
        if (commandeEnCours != null)
            throw new IllegalStateException("La table " + getLibelle() + " est déjà occupée par une autre commande.");
        commandeEnCours = commande;
        majStatut();
    }

    public void marquerPropre() {
        if (statut == StatutTable.NETTOYAGE) statut = StatutTable.LIBRE;
    }

    /** Recalcule le statut d'après l'état de la commande en cours. */
    public void majStatut() {
        if (commandeEnCours == null) return;
        if (commandeEnCours.isPaiementAmorce())                         statut = StatutTable.ADDITION;
        else if (commandeEnCours.prochaineSuiteAReclamer().isPresent()) statut = StatutTable.RECLAMEE;
        else                                                            statut = StatutTable.OCCUPEE;
    }

    public boolean estLibre()   { return statut == StatutTable.LIBRE; }
    public boolean estOccupee() { return commandeEnCours != null; }

    /** "T5" pour une table, "E2" pour un emplacement à emporter. */
    public String getLibelle() {
        return zone == Zone.EMPORTER ? "E" + (numero - 100) : "T" + numero;
    }

    public int getNumero()               { return numero; }
    public int getCapacite()             { return capacite; }
    public Zone getZone()                { return zone; }
    public int getCol()                  { return col; }
    public int getRow()                  { return row; }
    public StatutTable getStatut()       { return statut; }
    public Commande getCommandeEnCours() { return commandeEnCours; }

    @Override
    public String toString() {
        return getLibelle() + " — " + capacite + " pers. (" + zone.name().toLowerCase() + ")";
    }
}
