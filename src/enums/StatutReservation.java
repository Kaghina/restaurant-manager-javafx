package enums;

public enum StatutReservation {
    CONFIRMEE("Confirmée"),
    EN_ATTENTE("Liste d'attente"),
    ARRIVEE("Arrivée"),
    NO_SHOW("No-show"),
    ANNULEE("Annulée");

    private final String libelle;

    StatutReservation(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
