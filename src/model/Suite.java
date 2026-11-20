package model;

import enums.StatutCommande;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Une "suite" = un service d'une commande (entrées, puis plats, puis desserts…).
 * La suite 1 part directement en cuisine ; les suivantes attendent d'être réclamées.
 */
public class Suite {
    private final int numero;
    private final List<LigneCommande> lignes = new ArrayList<>();
    private StatutCommande statut = StatutCommande.EN_COURS;
    private LocalDateTime heureEnvoi;

    public Suite(int numero) { this.numero = numero; }

    public LigneCommande ajouterPlat(Plat plat, int quantite) {
        if (estEnvoyee()) throw new IllegalStateException("Suite déjà envoyée en cuisine");
        for (LigneCommande l : lignes) {
            if (l.getPlat().getId() == plat.getId() && !l.estPersonnalisee()) {
                l.setQuantite(l.getQuantite() + quantite);
                return l;
            }
        }
        LigneCommande ligne = new LigneCommande(plat, quantite);
        lignes.add(ligne);
        return ligne;
    }

    public void retirerLigne(LigneCommande ligne) {
        if (!estEnvoyee()) lignes.remove(ligne);
    }

    public double getTotal() {
        return lignes.stream().mapToDouble(LigneCommande::getSousTotal).sum();
    }

    /** Envoi en cuisine : décrémente les stocks. {@code lancer} = à préparer immédiatement. */
    public void envoyer(boolean lancer) {
        if (estEnvoyee() || estVide()) return;
        for (LigneCommande l : lignes) l.getPlat().consommer(l.getQuantite());
        statut = lancer ? StatutCommande.EN_CUISINE : StatutCommande.EN_ATTENTE;
        heureEnvoi = LocalDateTime.now();
    }

    /** Réclamer = demander à la cuisine de lancer cette suite. */
    public void reclamer() {
        if (statut == StatutCommande.EN_ATTENTE) statut = StatutCommande.EN_CUISINE;
    }

    public int quantite(Plat plat) {
        return lignes.stream().filter(l -> l.getPlat().getId() == plat.getId())
                .mapToInt(LigneCommande::getQuantite).sum();
    }

    public boolean estEnvoyee()  { return statut != StatutCommande.EN_COURS; }
    public boolean estEnAttente(){ return statut == StatutCommande.EN_ATTENTE; }
    public boolean estLancee()   { return statut == StatutCommande.EN_CUISINE; }
    public boolean estVide()     { return lignes.isEmpty(); }

    public int getNumero()                 { return numero; }
    public List<LigneCommande> getLignes() { return lignes; }
    public StatutCommande getStatut()      { return statut; }
    /** Réservé à l'import de données (historique de démonstration) : ne touche pas aux stocks. */
    public void setStatut(StatutCommande s){ this.statut = s; }
    public LocalDateTime getHeureEnvoi()   { return heureEnvoi; }
}
