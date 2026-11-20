package model;

import enums.ModePaiement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un encaissement (total ou partiel) d'une commande.
 * {@code montantDu} est la part réglée par ce paiement ; plusieurs modes peuvent être combinés.
 * Après {@link #finaliser()}, les montants par mode sont nets (la monnaie rendue est retirée des espèces).
 */
public class Paiement {
    private static int compteur = 1;

    private final int id;
    private final Commande commande;
    private final Map<ModePaiement, Double> montantsParMode = new LinkedHashMap<>();
    private final Map<LigneCommande, Double> repartition = new LinkedHashMap<>();
    private final double montantDu;
    private final LocalDateTime dateHeure;
    private double montantRecu;
    private double montantRendu;

    public Paiement(Commande commande, double montantDu) {
        this(commande, montantDu, LocalDateTime.now());
    }

    public Paiement(Commande commande, double montantDu, LocalDateTime dateHeure) {
        this.id = compteur++;
        this.commande = commande;
        this.montantDu = Math.max(0, montantDu);
        this.dateHeure = dateHeure;
    }

    public void ajouterMontant(ModePaiement mode, double montant) {
        if (montant <= 0) throw new IllegalArgumentException("Montant invalide");
        montantsParMode.merge(mode, montant, Double::sum);
    }

    public double getTotalSaisi() {
        return montantsParMode.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Calcule le rendu monnaie (uniquement possible sur les espèces). */
    public void finaliser() {
        montantRecu = getTotalSaisi();
        double excedent = montantRecu - montantDu;
        if (excedent > 0.005) {
            double especes = montantsParMode.getOrDefault(ModePaiement.ESPECES, 0.0);
            if (especes + 1e-9 < excedent)
                throw new IllegalStateException("Le rendu de monnaie n'est possible qu'en espèces");
            montantRendu = excedent;
            double net = especes - excedent;
            if (net < 0.005) montantsParMode.remove(ModePaiement.ESPECES);
            else montantsParMode.put(ModePaiement.ESPECES, net);
        }
    }

    /** Mémorise la part d'une ligne réglée par ce paiement (pour pouvoir l'annuler). */
    public void affecter(LigneCommande ligne, double montant) {
        repartition.merge(ligne, montant, Double::sum);
    }

    public Map<LigneCommande, Double> getRepartition() { return Collections.unmodifiableMap(repartition); }

    /**
     * Corrige la répartition par mode (erreur de saisie). Les nouveaux montants doivent
     * totaliser le montant dû ; le rendu monnaie est alors remis à zéro.
     */
    public void corrigerModes(Map<ModePaiement, Double> nouveaux) {
        double total = nouveaux.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - montantDu) > 0.005)
            throw new IllegalArgumentException("Le total des modes doit être égal à " + util.Format.euros(montantDu));
        montantsParMode.clear();
        nouveaux.forEach((m, v) -> { if (v > 0.004) montantsParMode.put(m, v); });
        montantRecu = montantDu;
        montantRendu = 0;
    }

    public int getId()                    { return id; }
    public Commande getCommande()         { return commande; }
    public Map<ModePaiement, Double> getMontantsParMode() { return Collections.unmodifiableMap(montantsParMode); }
    public double getMontantDu()          { return montantDu; }
    public double getMontantRecu()        { return montantRecu; }
    public double getMontantRendu()       { return montantRendu; }
    public LocalDateTime getDateHeure()   { return dateHeure; }
    public LocalDate getDate()            { return dateHeure.toLocalDate(); }
}
