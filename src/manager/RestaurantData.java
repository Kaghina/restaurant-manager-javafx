package manager;

import enums.CategoriePlat;
import enums.ModePaiement;
import enums.StatutCommande;
import enums.StatutReservation;
import enums.StatutTable;
import model.*;
import model.TableRestaurant.Zone;
import service.TicketService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Point d'accès unique aux données du restaurant (singleton, en mémoire).
 * Au démarrage : carte, ingrédients, tables, clients, réservations du jour
 * et 4 semaines d'historique de démonstration (pour les statistiques et la prédiction du rush).
 */
public final class RestaurantData {

    public static final String NOM_RESTAURANT = "Le Bistrot";

    /** Suppléments proposés en un clic : nom → prix. */
    public static final Map<String, Double> SUPPLEMENTS = new LinkedHashMap<>();
    static {
        SUPPLEMENTS.put("Burrata", 3.50);         SUPPLEMENTS.put("Jambon de Parme", 3.00);
        SUPPLEMENTS.put("Jambon blanc", 2.00);    SUPPLEMENTS.put("Chorizo", 2.50);
        SUPPLEMENTS.put("Oignons caramélisés", 1.50); SUPPLEMENTS.put("Tomates cerises", 1.50);
        SUPPLEMENTS.put("Champignons", 1.50);     SUPPLEMENTS.put("Avocat", 2.00);
        SUPPLEMENTS.put("Œuf", 1.00);             SUPPLEMENTS.put("Parmesan", 1.50);
        SUPPLEMENTS.put("Frites", 3.00);          SUPPLEMENTS.put("Salade", 2.00);
        SUPPLEMENTS.put("Sauce poivre", 1.50);    SUPPLEMENTS.put("Sauce roquefort", 1.50);
        SUPPLEMENTS.put("Chantilly", 1.00);       SUPPLEMENTS.put("Boule de glace", 2.00);
    }

    /** Sous-catégories de boissons : code → libellé d'onglet. */
    public static final Map<String, String> SOUS_CATEGORIES_BOISSON = new LinkedHashMap<>();
    static {
        SOUS_CATEGORIES_BOISSON.put("vin", "🍷 Vins");
        SOUS_CATEGORIES_BOISSON.put("cocktail", "🍸 Cocktails");
        SOUS_CATEGORIES_BOISSON.put("mocktail", "🍹 Sans alcool");
        SOUS_CATEGORIES_BOISSON.put("soft", "🍋 Softs");
        SOUS_CATEGORIES_BOISSON.put("chaud", "☕ Chauds");
        SOUS_CATEGORIES_BOISSON.put("digestif", "🥃 Digestifs");
    }

    private static RestaurantData instance;

    private final List<TableRestaurant> tables       = new ArrayList<>();
    private final List<Plat>            menu         = new ArrayList<>();
    private final List<Ingredient>      ingredients  = new ArrayList<>();
    private final List<Client>          clients      = new ArrayList<>();
    private final List<Reservation>     reservations = new ArrayList<>();
    private final List<Paiement>        paiements    = new ArrayList<>();
    private final List<Commande>        historique   = new ArrayList<>();

    private RestaurantData() {
        initTables();
        initCarte();
        initClientsEtReservations();
        genererHistoriqueDemo();
    }

    public static synchronized RestaurantData get() {
        if (instance == null) instance = new RestaurantData();
        return instance;
    }

    // ═══ REQUÊTES ══════════════════════════════════════════════

    public List<TableRestaurant> getTables() { return tables; }

    public List<TableRestaurant> getTablesZone(Zone zone) {
        return tables.stream().filter(t -> t.getZone() == zone).collect(Collectors.toList());
    }

    /** Tables réservables (hors emplacements à emporter). */
    public List<TableRestaurant> getTablesSalle() {
        return tables.stream().filter(t -> t.getZone() != Zone.EMPORTER).collect(Collectors.toList());
    }

    public List<Plat> getMenu() { return menu; }

    public List<Plat> getMenuCategorie(CategoriePlat cat) {
        return menu.stream().filter(p -> p.getCategorie() == cat).collect(Collectors.toList());
    }

    public List<Plat> getMenuSousCategorie(String sc) {
        return menu.stream().filter(p -> sc.equals(p.getSousCategorie())).collect(Collectors.toList());
    }

    public List<Ingredient>  getIngredients()  { return ingredients; }
    public List<Client>      getClients()      { return clients; }
    public List<Reservation> getReservations() { return reservations; }
    public List<Paiement>    getPaiements()    { return paiements; }
    /** Commandes clôturées (payées), de la plus ancienne à la plus récente. */
    public List<Commande>    getHistorique()   { return historique; }

    public Client trouverClientParTel(String tel) {
        String normalise = normaliserTel(tel);
        return clients.stream().filter(c -> normaliserTel(c.getTelephone()).equals(normalise))
                .findFirst().orElse(null);
    }

    public static String normaliserTel(String tel) {
        return tel == null ? "" : tel.replaceAll("[^0-9+]", "");
    }

    public void ajouterPaiement(Paiement p) { paiements.add(p); }

    public List<Paiement> paiementsDe(Commande cmd) {
        return paiements.stream().filter(p -> p.getCommande() == cmd).collect(Collectors.toList());
    }

    /** Clôture une commande entièrement payée et libère sa table. */
    public void cloturer(Commande cmd) {
        cmd.cloturer();
        historique.add(cmd);
        cmd.getTable().liberer();
    }

    /** Annule un paiement : les articles qu'il réglait redeviennent à payer. */
    public void annulerPaiement(Paiement p) {
        p.getRepartition().forEach(LigneCommande::annulerPaiement);
        paiements.remove(p);
    }

    /** Une commande ne peut être rouverte que le jour de son encaissement. */
    public boolean peutRouvrir(Commande cmd) {
        return historique.contains(cmd) && cmd.getDateCloture() != null
                && cmd.getDateCloture().toLocalDate().equals(LocalDate.now());
    }

    /**
     * Rouvre une table encaissée par erreur : ses paiements sont annulés et la commande
     * revient sur la table, à l'état "addition".
     */
    public void rouvrir(Commande cmd) {
        if (!peutRouvrir(cmd))
            throw new IllegalStateException("Seules les commandes encaissées aujourd'hui peuvent être rouvertes.");
        TableRestaurant t = cmd.getTable();
        if (t.estOccupee())
            throw new IllegalStateException("La table " + t.getLibelle() + " est déjà occupée par une autre commande.");
        for (Paiement p : paiementsDe(cmd)) annulerPaiement(p);
        historique.remove(cmd);
        cmd.rouvrir();
        t.rouvrir(cmd);
    }

    public String ticket(Commande cmd) {
        return TicketService.generer(cmd, paiementsDe(cmd), NOM_RESTAURANT);
    }

    // ── Réservations ─────────────────────────────────────────

    /** Prochaine réservation active du jour sur cette table (jusqu'à 30 min de retard). */
    public Optional<Reservation> prochaineReservation(TableRestaurant t) {
        LocalDateTime maintenant = LocalDateTime.now();
        return reservations.stream()
                .filter(r -> r.getTable() == t && r.estActive())
                .filter(r -> r.getDateHeure().toLocalDate().equals(maintenant.toLocalDate()))
                .filter(r -> !r.getDateHeure().isBefore(maintenant.minusMinutes(30)))
                .min(Comparator.comparing(Reservation::getDateHeure));
    }

    /** Statut affiché sur le plan : une table libre réservée dans les 2 h apparaît "Réservée". */
    public StatutTable statutAffiche(TableRestaurant t) {
        if (t.getStatut() != StatutTable.LIBRE) return t.getStatut();
        return prochaineReservation(t)
                .filter(r -> r.getDateHeure().isBefore(LocalDateTime.now().plus(Reservation.DUREE)))
                .map(r -> StatutTable.RESERVEE).orElse(StatutTable.LIBRE);
    }

    /** Réservation qui empêche de réserver {@code t} à {@code debut}, s'il y en a une. */
    public Optional<Reservation> conflit(TableRestaurant t, LocalDateTime debut) {
        return conflit(t, debut, null);
    }

    /** Idem, en ignorant {@code exclue} (la réservation en cours de modification). */
    public Optional<Reservation> conflit(TableRestaurant t, LocalDateTime debut, Reservation exclue) {
        return reservations.stream().filter(r -> r != exclue && r.chevauche(t, debut)).findFirst();
    }

    // ── Alertes ──────────────────────────────────────────────

    public List<Ingredient> ingredientsStockBas() {
        return ingredients.stream().filter(Ingredient::estStockBas).collect(Collectors.toList());
    }

    public List<Plat> platsIndisponibles() {
        return menu.stream().filter(p -> !p.estDisponible()).collect(Collectors.toList());
    }

    // ═══ INITIALISATION ════════════════════════════════════════

    private void initTables() {
        int[][] salle = {{1,2,0,0},{2,2,1,0},{3,4,2,0},{4,4,3,0},{5,4,0,1},{6,6,1,1},{7,6,2,1},{8,8,3,1}};
        for (int[] t : salle) tables.add(new TableRestaurant(t[0], t[1], Zone.SALLE, t[2], t[3]));
        int[][] terrasse = {{9,2,0,0},{10,2,1,0},{11,4,2,0},{12,4,3,0}};
        for (int[] t : terrasse) tables.add(new TableRestaurant(t[0], t[1], Zone.TERRASSE, t[2], t[3]));
        int[][] bar = {{13,2,0,0},{14,2,1,0},{15,4,2,0}};
        for (int[] t : bar) tables.add(new TableRestaurant(t[0], t[1], Zone.BAR, t[2], t[3]));
        for (int i = 1; i <= 4; i++) tables.add(new TableRestaurant(100 + i, 1, Zone.EMPORTER, i - 1, 0));
    }

    private void initCarte() {
        // Ingrédients suivis en stock
        Ingredient saumon   = ingredient("Saumon frais", 3.0, "kg", 0.8);
        Ingredient boeuf    = ingredient("Entrecôte", 12, "pièce", 3);
        Ingredient steak    = ingredient("Steak haché", 10, "pièce", 3);
        Ingredient pain     = ingredient("Pain burger", 12, "pièce", 4);
        Ingredient poulet   = ingredient("Suprême de volaille", 10, "pièce", 3);
        Ingredient thon     = ingredient("Thon rouge", 1.2, "kg", 0.4);
        Ingredient arborio  = ingredient("Riz arborio", 2.0, "kg", 0.5);
        Ingredient champi   = ingredient("Champignons", 1.5, "kg", 0.4);
        Ingredient pates    = ingredient("Pâtes fraîches", 2.5, "kg", 0.6);
        Ingredient oeufs    = ingredient("Œufs", 30, "pièce", 10);
        Ingredient foieGras = ingredient("Foie gras", 4, "portion", 3);
        Ingredient burrata  = ingredient("Burrata", 6, "pièce", 2);
        Ingredient tomates  = ingredient("Tomates", 3.0, "kg", 0.5);
        Ingredient chocolat = ingredient("Chocolat noir", 1.0, "kg", 0.2);
        Ingredient menthe   = ingredient("Menthe fraîche", 2, "botte", 0.5);

        // Entrées
        plat("Salade César", 9.50, CategoriePlat.ENTREE, new Recette().ajouter(poulet, 0.5).ajouter(oeufs, 1));
        plat("Soupe à l'oignon", 7.00, CategoriePlat.ENTREE, null);
        plat("Foie gras maison", 16.00, CategoriePlat.ENTREE, new Recette().ajouter(foieGras, 1));
        plat("Carpaccio de bœuf", 13.50, CategoriePlat.ENTREE, null);
        plat("Burrata & tomates", 12.00, CategoriePlat.ENTREE, new Recette().ajouter(burrata, 1).ajouter(tomates, 0.15));
        plat("Tartare de saumon", 13.00, CategoriePlat.ENTREE, new Recette().ajouter(saumon, 0.12));

        // Plats
        plat("Entrecôte frites", 24.00, CategoriePlat.PLAT, new Recette().ajouter(boeuf, 1));
        plat("Saumon grillé", 19.50, CategoriePlat.PLAT, new Recette().ajouter(saumon, 0.2));
        plat("Suprême de volaille", 16.00, CategoriePlat.PLAT, new Recette().ajouter(poulet, 1));
        plat("Risotto aux champignons", 15.00, CategoriePlat.PLAT, new Recette().ajouter(arborio, 0.1).ajouter(champi, 0.15));
        plat("Burger maison", 14.00, CategoriePlat.PLAT, new Recette().ajouter(steak, 1).ajouter(pain, 1));
        plat("Pizza Margherita", 12.00, CategoriePlat.PLAT, new Recette().ajouter(tomates, 0.1));
        plat("Pâtes carbonara", 13.00, CategoriePlat.PLAT, new Recette().ajouter(pates, 0.15).ajouter(oeufs, 2));
        plat("Pavé de thon", 21.00, CategoriePlat.PLAT, new Recette().ajouter(thon, 0.2));

        // Desserts
        plat("Crème brûlée", 7.00, CategoriePlat.DESSERT, new Recette().ajouter(oeufs, 2));
        plat("Moelleux au chocolat", 7.50, CategoriePlat.DESSERT, new Recette().ajouter(chocolat, 0.08).ajouter(oeufs, 1));
        plat("Tiramisu", 6.50, CategoriePlat.DESSERT, null).setStock(8);
        plat("Tarte Tatin", 7.00, CategoriePlat.DESSERT, null).setStock(6);
        plat("Île flottante", 6.00, CategoriePlat.DESSERT, new Recette().ajouter(oeufs, 2));
        plat("Sorbet 3 boules", 6.50, CategoriePlat.DESSERT, null);

        // Vins (verre / bouteille)
        vin("Bordeaux", 6.50, 38, "rouge");          vin("Côtes du Rhône", 5.50, 32, "rouge");
        vin("Malbec", 7.00, 42, "rouge");            vin("Pinot Noir d'Alsace", 7.50, 45, "rouge");
        vin("Saint-Émilion", 9.00, 55, "rouge");
        vin("Chardonnay", 6.50, 38, "blanc");        vin("Sauvignon Blanc", 6.00, 35, "blanc");
        vin("Bourgogne Blanc", 8.00, 48, "blanc");   vin("Riesling", 7.00, 42, "blanc");
        vin("Côtes de Provence", 6.50, 38, "rose");  vin("Rosé d'Anjou", 5.00, 29, "rose");

        boisson("cocktail", 10.00, "Mojito", menthe); boisson("cocktail", 11.00, "Cosmopolitan", null);
        boisson("cocktail", 10.00, "Spritz", null);   boisson("cocktail", 12.00, "Negroni", null);
        boisson("cocktail", 11.00, "Margarita", null);boisson("cocktail", 11.00, "Moscow Mule", null);

        boisson("mocktail", 8.00, "Virgin Mojito", menthe); boisson("mocktail", 8.00, "Sunrise tropical", null);
        boisson("mocktail", 6.50, "Limonade maison", null); boisson("mocktail", 7.00, "Smoothie fruits rouges", null);

        for (String s : new String[]{"Cola", "Cola zéro", "Eau plate 50 cl", "Eau gazeuse 50 cl",
                "Jus d'orange", "Jus de pomme", "Limonade", "Thé glacé"})
            boisson("soft", s.startsWith("Eau") ? 3.00 : s.startsWith("Jus") ? 4.00 : 3.50, s, null);

        boisson("chaud", 2.50, "Café", null);          boisson("chaud", 3.50, "Double expresso", null);
        boisson("chaud", 4.00, "Cappuccino", null);    boisson("chaud", 4.00, "Latte", null);
        boisson("chaud", 3.50, "Thé", null);           boisson("chaud", 3.50, "Infusion", null);
        Plat choco = boisson("chaud", 4.50, "Chocolat chaud", null);
        choco.setRecette(new Recette().ajouter(chocolat, 0.03));

        for (Object[] d : new Object[][]{{"Calvados", 6.5}, {"Armagnac", 7.0}, {"Cognac", 7.5},
                {"Limoncello", 5.5}, {"Porto", 5.0}, {"Amaretto", 5.5}, {"Chartreuse", 6.0}})
            boisson("digestif", (Double) d[1], (String) d[0], null);
    }

    private Ingredient ingredient(String nom, double qte, String unite, double seuil) {
        Ingredient i = new Ingredient(nom, qte, unite, seuil);
        ingredients.add(i);
        return i;
    }

    private Plat plat(String nom, double prix, CategoriePlat cat, Recette recette) {
        Plat p = new Plat(nom, prix, cat).setRecette(recette);
        menu.add(p);
        return p;
    }

    private void vin(String nom, double verre, double bouteille, String couleur) {
        menu.add(Plat.vin(nom, verre, bouteille, couleur));
    }

    private Plat boisson(String sousCategorie, double prix, String nom, Ingredient menthe) {
        Plat p = new Plat(nom, prix, CategoriePlat.BOISSON, sousCategorie);
        if (menthe != null) p.setRecette(new Recette().ajouter(menthe, 0.1));
        menu.add(p);
        return p;
    }

    private void initClientsEtReservations() {
        Client marie = new Client("Dupont", "Marie", "06 12 34 56 78", "marie.dupont@email.com");
        marie.setNombreVisites(7);
        marie.setNotes("Allergie aux fruits à coque");
        Client paul = new Client("Martin", "Paul", "06 98 76 54 32", "paul.martin@email.com");
        paul.setNombreVisites(2);
        Client lea = new Client("Bernard", "Léa", "07 11 22 33 44", "");
        Client karim = new Client("Haddad", "Karim", "06 55 44 33 22", "k.haddad@email.com");
        karim.setNombreVisites(5);
        karim.setNotes("Préfère la terrasse");
        clients.addAll(List.of(marie, paul, lea, karim));

        LocalDate today = LocalDate.now();
        Reservation r1 = new Reservation(paul, table(3), today.atTime(12, 30), 3);
        Reservation r2 = new Reservation(marie, table(6), today.atTime(20, 0), 5);
        r2.setCommentaire("Anniversaire — prévoir une bougie");
        Reservation r3 = new Reservation(karim, table(11), today.atTime(20, 30), 4);
        Reservation r4 = new Reservation(lea, table(2), today.plusDays(1).atTime(12, 15), 2);
        Reservation r5 = new Reservation(paul, table(8), today.plusDays(1).atTime(21, 0), 8);
        r5.changerStatut(StatutReservation.EN_ATTENTE);
        reservations.addAll(List.of(r1, r2, r3, r4, r5));
    }

    private TableRestaurant table(int numero) {
        return tables.stream().filter(t -> t.getNumero() == numero).findFirst().orElseThrow();
    }

    /**
     * Génère 4 semaines d'activité passée (fermé le lundi, plus de monde le week-end)
     * pour alimenter les statistiques et la prédiction des heures de rush.
     * Ces commandes ne touchent pas aux stocks.
     */
    private void genererHistoriqueDemo() {
        Random rnd = new Random(2026);
        List<TableRestaurant> salle = getTablesSalle();
        List<Plat> entrees = getMenuCategorie(CategoriePlat.ENTREE);
        List<Plat> plats = getMenuCategorie(CategoriePlat.PLAT);
        List<Plat> desserts = getMenuCategorie(CategoriePlat.DESSERT);
        List<Plat> boissons = getMenuCategorie(CategoriePlat.BOISSON);
        LocalDate today = LocalDate.now();

        for (int j = 28; j >= 1; j--) {
            LocalDate jour = today.minusDays(j);
            DayOfWeek dow = jour.getDayOfWeek();
            if (dow == DayOfWeek.MONDAY) continue;
            double affluence = switch (dow) {
                case FRIDAY, SATURDAY -> 1.7;
                case SUNDAY -> 1.3;
                default -> 1.0;
            };
            int midi = (int) Math.round((5 + rnd.nextInt(4)) * affluence);
            int soir = (int) Math.round((4 + rnd.nextInt(4)) * affluence);
            for (int i = 0; i < midi; i++) commandeDemo(jour, 12 * 60 + 45, 35, 11 * 60 + 45, 14 * 60 + 15, rnd, salle, entrees, plats, desserts, boissons);
            for (int i = 0; i < soir; i++) commandeDemo(jour, 20 * 60 + 15, 50, 19 * 60, 22 * 60 + 30, rnd, salle, entrees, plats, desserts, boissons);
        }
        historique.sort(Comparator.comparing(Commande::getDateHeure));
        paiements.sort(Comparator.comparing(Paiement::getDateHeure));
    }

    private void commandeDemo(LocalDate jour, int moyenneMin, int ecartMin, int minMin, int maxMin, Random rnd,
                              List<TableRestaurant> salle, List<Plat> entrees, List<Plat> plats,
                              List<Plat> desserts, List<Plat> boissons) {
        int minute = (int) Math.round(moyenneMin + rnd.nextGaussian() * ecartMin);
        minute = Math.max(minMin, Math.min(maxMin, minute));
        LocalDateTime arrivee = jour.atTime(LocalTime.of(minute / 60, minute % 60));

        TableRestaurant t = salle.get(rnd.nextInt(salle.size()));
        int couverts = 1 + rnd.nextInt(t.getCapacite());
        Commande cmd = new Commande(t, couverts, arrivee);
        for (int c = 0; c < couverts; c++) {
            if (rnd.nextDouble() < 0.45) cmd.getSuiteActive().ajouterPlat(entrees.get(rnd.nextInt(entrees.size())), 1);
            cmd.getSuiteActive().ajouterPlat(plats.get(rnd.nextInt(plats.size())), 1);
            if (rnd.nextDouble() < 0.5) cmd.getSuiteActive().ajouterPlat(desserts.get(rnd.nextInt(desserts.size())), 1);
            cmd.getSuiteActive().ajouterPlat(boissons.get(rnd.nextInt(boissons.size())), 1);
        }
        cmd.getSuiteActive().setStatut(StatutCommande.EN_CUISINE);

        LocalDateTime fin = arrivee.plusMinutes(45 + rnd.nextInt(50));
        double total = cmd.getTotal();
        Paiement p = new Paiement(cmd, total, fin);
        double tirage = rnd.nextDouble();
        ModePaiement mode = tirage < 0.6 ? ModePaiement.CARTE : tirage < 0.82 ? ModePaiement.ESPECES
                : tirage < 0.96 ? ModePaiement.TICKET_RESTAURANT : ModePaiement.CHEQUE;
        p.ajouterMontant(mode, total);
        p.finaliser();
        cmd.cloturer(fin);
        paiements.add(p);
        historique.add(cmd);
    }
}
