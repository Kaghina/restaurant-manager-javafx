package service;

import enums.ModePaiement;
import manager.RestaurantData;
import model.Commande;
import model.LigneCommande;
import model.Paiement;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Calculs statistiques : CA, couverts, ticket moyen, top ventes, prédiction des heures de rush. */
public class StatistiqueService {

    /** Plage horaire affichée dans les graphiques. */
    public static final int HEURE_DEBUT = 11;
    public static final int HEURE_FIN = 23;

    private final RestaurantData data;

    public StatistiqueService(RestaurantData data) { this.data = data; }

    /** Paiements d'une journée (ou de tout l'historique si {@code date} est null). */
    public List<Paiement> paiements(LocalDate date) {
        if (date == null) return data.getPaiements();
        return data.getPaiements().stream().filter(p -> p.getDate().equals(date)).collect(Collectors.toList());
    }

    public double chiffreAffaires(List<Paiement> paiements) {
        return paiements.stream().mapToDouble(Paiement::getMontantDu).sum();
    }

    /** Commandes concernées (une commande payée en plusieurs fois n'est comptée qu'une fois). */
    public Set<Commande> commandes(List<Paiement> paiements) {
        return paiements.stream().map(Paiement::getCommande).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int couverts(Collection<Commande> commandes) {
        return commandes.stream().mapToInt(Commande::getNombreCouverts).sum();
    }

    public double ticketMoyen(List<Paiement> paiements) {
        int n = commandes(paiements).size();
        return n == 0 ? 0 : chiffreAffaires(paiements) / n;
    }

    public Map<ModePaiement, Double> parMode(List<Paiement> paiements) {
        Map<ModePaiement, Double> res = new EnumMap<>(ModePaiement.class);
        for (ModePaiement m : ModePaiement.values()) res.put(m, 0.0);
        for (Paiement p : paiements)
            p.getMontantsParMode().forEach((m, v) -> res.merge(m, v, Double::sum));
        return res;
    }

    /** Articles les plus vendus (nom → quantité), triés par quantité décroissante. */
    public LinkedHashMap<String, Integer> topArticles(Collection<Commande> commandes, int limite) {
        Map<String, Integer> quantites = new HashMap<>();
        for (Commande c : commandes)
            for (LigneCommande l : c.getToutesLignes())
                quantites.merge(l.getPlat().getNom(), l.getQuantite(), Integer::sum);
        return quantites.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limite)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** Couverts par heure d'arrivée (index = heure 0..23). */
    public double[] couvertsParHeure(Collection<Commande> commandes) {
        double[] res = new double[24];
        for (Commande c : commandes) res[c.getDateHeure().getHour()] += c.getNombreCouverts();
        return res;
    }

    /** CA par heure d'arrivée des clients (index = heure 0..23). */
    public double[] caParHeure(List<Paiement> paiements) {
        double[] res = new double[24];
        for (Paiement p : paiements) res[p.getCommande().getDateHeure().getHour()] += p.getMontantDu();
        return res;
    }

    /**
     * Prévision du nombre de couverts par heure pour une date : moyenne des couverts observés
     * les mêmes jours de la semaine dans l'historique (journées antérieures uniquement).
     */
    public double[] previsionCouverts(LocalDate date) {
        DayOfWeek jour = date.getDayOfWeek();
        Map<LocalDate, List<Commande>> parJour = commandes(data.getPaiements()).stream()
                .filter(c -> c.getDateHeure().toLocalDate().isBefore(date))
                .filter(c -> c.getDateHeure().getDayOfWeek() == jour)
                .collect(Collectors.groupingBy(c -> c.getDateHeure().toLocalDate()));
        double[] res = new double[24];
        if (parJour.isEmpty()) return res;
        for (List<Commande> cmds : parJour.values()) {
            double[] h = couvertsParHeure(cmds);
            for (int i = 0; i < 24; i++) res[i] += h[i];
        }
        for (int i = 0; i < 24; i++) res[i] /= parJour.size();
        return res;
    }

    /** Nombre de journées de référence utilisées pour la prévision. */
    public long joursDeReference(LocalDate date) {
        return commandes(data.getPaiements()).stream()
                .map(c -> c.getDateHeure().toLocalDate())
                .filter(d -> d.isBefore(date) && d.getDayOfWeek() == date.getDayOfWeek())
                .distinct().count();
    }

    /** Heure la plus chargée d'une série, ou -1 si tout est à zéro. */
    public static int heurePic(double[] serie) {
        int best = -1;
        for (int i = 0; i < serie.length; i++)
            if (serie[i] > 0 && (best < 0 || serie[i] > serie[best])) best = i;
        return best;
    }

    /** Couverts moyens par jour de la semaine sur tout l'historique. */
    public Map<DayOfWeek, Double> couvertsMoyensParJour() {
        Map<DayOfWeek, Double> res = new EnumMap<>(DayOfWeek.class);
        Map<LocalDate, Integer> parDate = new HashMap<>();
        for (Commande c : commandes(data.getPaiements()))
            parDate.merge(c.getDateHeure().toLocalDate(), c.getNombreCouverts(), Integer::sum);
        for (DayOfWeek d : DayOfWeek.values()) {
            res.put(d, parDate.entrySet().stream().filter(e -> e.getKey().getDayOfWeek() == d)
                    .mapToInt(Map.Entry::getValue).average().orElse(0));
        }
        return res;
    }
}
