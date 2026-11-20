package model;

/** Ingrédient en stock (ex : 3 kg de saumon, seuil d'alerte 0,8 kg). */
public class Ingredient {
    private static int compteur = 1;

    private final int id;
    private final String nom;
    private final String unite;
    private double quantiteStock;
    private double seuilAlerte;

    public Ingredient(String nom, double quantiteStock, String unite, double seuilAlerte) {
        this.id = compteur++;
        this.nom = nom;
        this.quantiteStock = Math.max(0, quantiteStock);
        this.unite = unite;
        this.seuilAlerte = seuilAlerte;
    }

    public void consommer(double quantite) {
        quantiteStock = Math.max(0, quantiteStock - quantite);
    }

    public void reapprovisionner(double quantite) {
        if (quantite <= 0) throw new IllegalArgumentException("La quantité doit être positive");
        quantiteStock += quantite;
    }

    public boolean estEnRupture() { return quantiteStock <= 1e-9; }
    public boolean estStockBas()  { return quantiteStock <= seuilAlerte; }

    public int    getId()            { return id; }
    public String getNom()           { return nom; }
    public String getUnite()         { return unite; }
    public double getQuantiteStock() { return quantiteStock; }
    public double getSeuilAlerte()   { return seuilAlerte; }
    public void   setSeuilAlerte(double s) { this.seuilAlerte = Math.max(0, s); }

    @Override
    public String toString() { return nom; }
}
