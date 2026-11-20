package enums;

/** Statut d'une commande ou d'une suite (service) de commande. */
public enum StatutCommande {
    EN_COURS("En saisie"),
    EN_ATTENTE("En attente de réclame"),
    EN_CUISINE("En cuisine"),
    PAYEE("Payée"),
    ANNULEE("Annulée");

    private final String libelle;

    StatutCommande(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
