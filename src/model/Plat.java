package model;

import enums.CategoriePlat;

/**
 * Article de la carte (plat, dessert, boisson…).
 * La disponibilité dépend : d'une mise en rupture manuelle, d'un stock à l'unité
 * (-1 = illimité) et de la recette (ingrédients en stock).
 */
public class Plat {
    private static int compteur = 1;

    private final int id;
    private final String nom;
    private double prix;
    private final CategoriePlat categorie;
    private final String sousCategorie;  // "vin", "cocktail", "soft"… ("" pour la cuisine)

    private double prixBouteille;        // 0 = pas de bouteille
    private String couleurVin;           // "rouge" | "blanc" | "rose" | null
    private Plat parent;                 // pour la variante bouteille d'un vin
    private Plat varianteBouteille;

    private int stock = -1;              // -1 = illimité
    private boolean enRupture;
    private Recette recette;

    public Plat(String nom, double prix, CategoriePlat categorie) {
        this(nom, prix, categorie, "");
    }

    public Plat(String nom, double prix, CategoriePlat categorie, String sousCategorie) {
        if (prix < 0) throw new IllegalArgumentException("Prix négatif");
        this.id = compteur++;
        this.nom = nom;
        this.prix = prix;
        this.categorie = categorie;
        this.sousCategorie = sousCategorie == null ? "" : sousCategorie;
    }

    /** Fabrique un vin vendu au verre et à la bouteille. */
    public static Plat vin(String nom, double prixVerre, double prixBouteille, String couleur) {
        Plat p = new Plat(nom, prixVerre, CategoriePlat.BOISSON, "vin");
        p.prixBouteille = prixBouteille;
        p.couleurVin = couleur;
        return p;
    }

    // ── Disponibilité ─────────────────────────────────────────

    public boolean peutServir(int portions) {
        if (parent != null) return parent.estDisponible();
        if (enRupture) return false;
        if (stock >= 0 && stock < portions) return false;
        return recette == null || recette.estRealisable(portions);
    }

    public boolean estDisponible() { return peutServir(1); }

    /** Rupture déclenchée automatiquement (stock ou ingrédient épuisé), pas manuellement. */
    public boolean estEnRuptureAuto() { return !enRupture && !estDisponible(); }

    public void consommer(int quantite) {
        if (parent != null) return;               // la bouteille n'a pas de suivi de stock propre
        if (stock > 0) stock = Math.max(0, stock - quantite);
        if (recette != null) recette.consommer(quantite);
    }

    public void mettreEnRupture()  { enRupture = true; }

    public void rendreDisponible() {
        enRupture = false;
        if (stock == 0) stock = -1;
    }

    public String getStatutAffichage() {
        if (!estDisponible()) return "Rupture";
        if (stock > 0 && stock <= 3) return "⚠ " + stock + " restant(s)";
        return "";
    }

    // ── Vin / bouteille ───────────────────────────────────────

    public boolean hasBouteille() { return prixBouteille > 0; }

    public Plat getVarianteBouteille() {
        if (!hasBouteille()) return null;
        if (varianteBouteille == null) {
            varianteBouteille = new Plat(nom + " (bouteille)", prixBouteille, CategoriePlat.BOISSON, "vin");
            varianteBouteille.couleurVin = couleurVin;
            varianteBouteille.parent = this;
        }
        return varianteBouteille;
    }

    /** TVA restauration : 20 % sur l'alcool, 10 % sur le reste. */
    public double getTauxTva() {
        boolean alcool = categorie == CategoriePlat.BOISSON
                && ("vin".equals(sousCategorie) || "cocktail".equals(sousCategorie)
                    || "digestif".equals(sousCategorie));
        return alcool ? 0.20 : 0.10;
    }

    // ── Accesseurs ────────────────────────────────────────────

    public int    getId()               { return id; }
    public String getNom()              { return nom; }
    public double getPrix()             { return prix; }
    public void   setPrix(double prix)  { if (prix >= 0) this.prix = prix; }
    public double getPrixBouteille()    { return prixBouteille; }
    public CategoriePlat getCategorie() { return categorie; }
    public String getSousCategorie()    { return sousCategorie; }
    public String getCouleurVin()       { return couleurVin; }
    public boolean isVin()              { return "vin".equals(sousCategorie); }
    public int  getStock()              { return stock; }
    public void setStock(int stock)     { this.stock = Math.max(-1, stock); }
    public boolean isEnRupture()        { return enRupture; }
    public Recette getRecette()         { return recette; }
    public Plat setRecette(Recette r)   { this.recette = r; return this; }

    @Override
    public String toString() { return nom; }
}
