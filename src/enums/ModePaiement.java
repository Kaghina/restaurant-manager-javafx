package enums;

/** Modes de paiement acceptés. Un paiement "mixte" = plusieurs modes sur un même encaissement. */
public enum ModePaiement {
    ESPECES("Espèces", "💶"),
    CARTE("Carte bancaire", "💳"),
    CHEQUE("Chèque", "✍"),
    TICKET_RESTAURANT("Ticket restaurant", "🎫");

    private final String libelle;
    private final String icone;

    ModePaiement(String libelle, String icone) {
        this.libelle = libelle;
        this.icone = icone;
    }

    public String getLibelle() { return libelle; }
    public String getIcone()   { return icone; }

    @Override
    public String toString() { return libelle; }
}
