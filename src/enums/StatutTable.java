package enums;

/** Statut d'une table, avec la classe CSS associée pour le plan de salle. */
public enum StatutTable {
    LIBRE("Libre", "table-libre"),
    RESERVEE("Réservée", "table-reservee"),
    OCCUPEE("Occupée", "table-occupee"),
    RECLAMEE("Suite à réclamer", "table-reclamee"),
    ADDITION("Addition", "table-addition"),
    NETTOYAGE("À débarrasser", "table-nettoyage");

    private final String libelle;
    private final String styleClass;

    StatutTable(String libelle, String styleClass) {
        this.libelle = libelle;
        this.styleClass = styleClass;
    }

    public String getLibelle()    { return libelle; }
    public String getStyleClass() { return styleClass; }

    @Override
    public String toString() { return libelle; }
}
