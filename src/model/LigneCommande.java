package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Une ligne de commande : un article, sa quantité et ses personnalisations. */
public class LigneCommande {

    private final Plat plat;
    private int quantite;
    private String note = "";
    private boolean offert;
    private double remisePourcentage;

    /** Suppléments : nom → prix unitaire additionnel. */
    private final Map<String, Double> supplements = new LinkedHashMap<>();
    /** Ingrédients retirés ("oignon", "sauce"…). */
    private final List<String> ingredientsRetires = new ArrayList<>();

    public LigneCommande(Plat plat, int quantite) {
        if (quantite < 1) throw new IllegalArgumentException("Quantité invalide");
        this.plat = plat;
        this.quantite = quantite;
    }

    // ── Prix ─────────────────────────────────────────────────

    public double getPrixUnitaire() {
        return plat.getPrix() + supplements.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getSousTotalBrut() { return getPrixUnitaire() * quantite; }

    public double getSousTotal() {
        if (offert) return 0;
        return getSousTotalBrut() * (1 - remisePourcentage / 100.0);
    }

    // ── Paiement (gestion des additions partagées) ───────────

    private double montantPaye;

    public void payer(double montant) { montantPaye += Math.max(0, montant); }
    public void annulerPaiement(double montant) { montantPaye = Math.max(0, montantPaye - Math.max(0, montant)); }
    public double getMontantPaye()    { return montantPaye; }
    public double getResteAPayer()    { return Math.max(0, getSousTotal() - montantPaye); }
    public boolean estSoldee()        { return getResteAPayer() < 0.005; }

    /** Une ligne personnalisée n'est jamais fusionnée avec un nouvel ajout du même plat. */
    public boolean estPersonnalisee() {
        return !supplements.isEmpty() || !ingredientsRetires.isEmpty() || !note.isBlank();
    }

    // ── Suppléments / retraits ───────────────────────────────

    public void ajouterSupplement(String nom, double prixUnitaire) {
        supplements.merge(nom, Math.max(0, prixUnitaire), Double::sum);
    }

    public void retirerSupplement(String nom) { supplements.remove(nom); }

    public Map<String, Double> getSupplements() { return supplements; }

    public void ajouterIngredientRetire(String nom) {
        if (!ingredientsRetires.contains(nom)) ingredientsRetires.add(nom);
    }

    public void retirerIngredientRetire(String nom) { ingredientsRetires.remove(nom); }

    public List<String> getIngredientsRetires() { return ingredientsRetires; }

    // ── Accesseurs ───────────────────────────────────────────

    public Plat    getPlat()      { return plat; }
    public int     getQuantite()  { return quantite; }
    public void    setQuantite(int q) { if (q >= 1) this.quantite = q; }
    public String  getNote()      { return note; }
    public void    setNote(String note) { this.note = note == null ? "" : note; }
    public boolean isOffert()     { return offert; }
    public void    setOffert(boolean offert) { this.offert = offert; }
    public double  getRemisePourcentage() { return remisePourcentage; }
    public void    setRemisePourcentage(double r) { this.remisePourcentage = Math.max(0, Math.min(100, r)); }
}
