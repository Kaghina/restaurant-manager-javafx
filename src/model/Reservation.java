package model;

import enums.StatutReservation;

import java.time.Duration;
import java.time.LocalDateTime;

/** Réservation d'une table. Elle ne modifie pas directement le statut de la table. */
public class Reservation {
    /** Durée pendant laquelle une table est considérée comme prise par une réservation. */
    public static final Duration DUREE = Duration.ofHours(2);

    private static int compteur = 1;

    private final int id;
    private final Client client;
    private TableRestaurant table;
    private LocalDateTime dateHeure;
    private int nombrePersonnes;
    private StatutReservation statut = StatutReservation.CONFIRMEE;
    private String commentaire = "";

    public Reservation(Client client, TableRestaurant table, LocalDateTime dateHeure, int nombrePersonnes) {
        this.id = compteur++;
        this.client = client;
        this.table = table;
        this.dateHeure = dateHeure;
        this.nombrePersonnes = nombrePersonnes;
    }

    public void confirmer() {
        if (statut == StatutReservation.EN_ATTENTE) statut = StatutReservation.CONFIRMEE;
    }

    public void marquerArrivee() {
        if (statut == StatutReservation.ARRIVEE) return;
        statut = StatutReservation.ARRIVEE;
        client.incrementerVisites();
    }

    public void marquerNoShow() { statut = StatutReservation.NO_SHOW; }
    public void annuler()       { statut = StatutReservation.ANNULEE; }

    /** Change le statut en appliquant les règles métier (visites du client…). */
    public void changerStatut(StatutReservation nouveau) {
        switch (nouveau) {
            case ARRIVEE    -> marquerArrivee();
            case NO_SHOW    -> marquerNoShow();
            case ANNULEE    -> annuler();
            case CONFIRMEE, EN_ATTENTE -> statut = nouveau;
        }
    }

    public boolean estActive() {
        return statut == StatutReservation.CONFIRMEE || statut == StatutReservation.EN_ATTENTE;
    }

    /** Vrai si cette réservation active occupe {@code t} sur le créneau commençant à {@code debut}. */
    public boolean chevauche(TableRestaurant t, LocalDateTime debut) {
        if (!estActive() || table != t) return false;
        return Duration.between(dateHeure, debut).abs().compareTo(DUREE) < 0;
    }

    public int getId()                     { return id; }
    public Client getClient()              { return client; }
    public TableRestaurant getTable()      { return table; }
    public void setTable(TableRestaurant t){ this.table = t; }
    public LocalDateTime getDateHeure()    { return dateHeure; }
    public void setDateHeure(LocalDateTime d) { this.dateHeure = d; }
    public int getNombrePersonnes()        { return nombrePersonnes; }
    public void setNombrePersonnes(int n)  { this.nombrePersonnes = n; }
    public StatutReservation getStatut()   { return statut; }
    public String getCommentaire()         { return commentaire; }
    public void setCommentaire(String c)   { this.commentaire = c == null ? "" : c; }
}
