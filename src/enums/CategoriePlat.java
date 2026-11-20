package enums;

/** Catégories de la carte. */
public enum CategoriePlat {
    ENTREE("Entrées"),
    PLAT("Plats"),
    DESSERT("Desserts"),
    BOISSON("Boissons");

    private final String libelle;

    CategoriePlat(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    @Override
    public String toString() { return libelle; }
}
