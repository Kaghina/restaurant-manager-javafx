package service;

import enums.ModePaiement;
import model.Commande;
import model.LigneCommande;
import model.Paiement;
import model.Suite;
import util.Format;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Génère le ticket de caisse texte d'une commande (avec détail TVA et paiements). */
public final class TicketService {
    private static final int LARGEUR = 40;

    private TicketService() { }

    public static String generer(Commande cmd, List<Paiement> paiements, String nomRestaurant) {
        StringBuilder sb = new StringBuilder();
        String sep = "=".repeat(LARGEUR) + "\n";
        String tiret = "-".repeat(LARGEUR) + "\n";

        sb.append(sep).append(centrer(nomRestaurant.toUpperCase())).append(sep);
        LocalDateTime date = cmd.getDateCloture() != null ? cmd.getDateCloture() : LocalDateTime.now();
        sb.append("Date     : ").append(Format.dateHeure(date)).append('\n');
        sb.append("Commande : #").append(cmd.getId());
        if (cmd.estAEmporter()) {
            sb.append("  (à emporter)\n");
            if (!cmd.getNomClient().isEmpty()) sb.append("Client   : ").append(cmd.getNomClient()).append('\n');
        } else {
            sb.append("\nTable    : ").append(cmd.getTable().getLibelle())
              .append("   Couverts : ").append(cmd.getNombreCouverts()).append('\n');
        }
        sb.append(tiret);

        Map<Double, double[]> tva = new TreeMap<>(); // taux → {TTC}
        for (Suite s : cmd.getSuites()) {
            for (LigneCommande l : s.getLignes()) {
                String nom = l.getQuantite() + " x " + l.getPlat().getNom();
                sb.append(ligne(nom, Format.euros(l.getSousTotal())));
                for (Map.Entry<String, Double> sup : l.getSupplements().entrySet())
                    sb.append("    + ").append(sup.getKey()).append('\n');
                if (l.isOffert())                      sb.append("    (offert)\n");
                else if (l.getRemisePourcentage() > 0) sb.append("    (remise ").append(Format.nombre(l.getRemisePourcentage())).append(" %)\n");
                tva.computeIfAbsent(l.getPlat().getTauxTva(), k -> new double[1])[0] += l.getSousTotal();
            }
        }
        sb.append(tiret);

        double brut = cmd.getTotalBrut();
        double net = cmd.getTotal();
        if (brut - net > 0.005) {
            sb.append(ligne("Sous-total", Format.euros(brut)));
            sb.append(ligne("Remises / offerts", "-" + Format.euros(brut - net)));
        }
        sb.append(ligne("TOTAL TTC", Format.euros(net)));
        for (Map.Entry<Double, double[]> e : tva.entrySet()) {
            double ttc = e.getValue()[0];
            double montantTva = ttc - ttc / (1 + e.getKey());
            sb.append(ligne("  dont TVA " + Format.nombre(e.getKey() * 100) + " %", Format.euros(montantTva)));
        }
        sb.append(tiret);

        for (Paiement p : paiements) {
            for (Map.Entry<ModePaiement, Double> e : p.getMontantsParMode().entrySet())
                sb.append(ligne(e.getKey().getLibelle(), Format.euros(e.getValue())));
            if (p.getMontantRendu() > 0.005) {
                sb.append(ligne("  Espèces reçues", Format.euros(p.getMontantsParMode()
                        .getOrDefault(ModePaiement.ESPECES, 0.0) + p.getMontantRendu())));
                sb.append(ligne("  Rendu monnaie", Format.euros(p.getMontantRendu())));
            }
        }
        sb.append(sep).append(centrer("Merci et à bientôt !")).append(sep);
        return sb.toString();
    }

    /** Bon de préparation envoyé en cuisine pour une suite. */
    public static String bonCuisine(Commande cmd, Suite suite) {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(30)).append('\n');
        sb.append(cmd.estAEmporter() ? "À EMPORTER " : "TABLE ").append(cmd.getTable().getLibelle())
          .append("  —  ").append(cmd.getNombreCouverts()).append(" couv.\n");
        sb.append("Suite ").append(suite.getNumero()).append("  (").append(suite.getStatut().getLibelle()).append(")\n");
        if (suite.getHeureEnvoi() != null) sb.append("Envoyée à ").append(Format.heure(suite.getHeureEnvoi())).append('\n');
        sb.append("=".repeat(30)).append('\n');
        for (LigneCommande l : suite.getLignes()) {
            sb.append(l.getQuantite()).append(" x ").append(l.getPlat().getNom()).append('\n');
            l.getSupplements().keySet().forEach(s -> sb.append("    + ").append(s).append('\n'));
            l.getIngredientsRetires().forEach(r -> sb.append("    - SANS ").append(r.toUpperCase()).append('\n'));
            if (!l.getNote().isBlank()) sb.append("    ! ").append(l.getNote()).append('\n');
        }
        return sb.toString();
    }

    private static String ligne(String gauche, String droite) {
        int espace = LARGEUR - droite.length();
        if (gauche.length() >= espace) gauche = gauche.substring(0, Math.max(0, espace - 2)) + "…";
        return String.format("%-" + espace + "s%s", gauche, droite) + "\n";
    }

    private static String centrer(String texte) {
        int marge = Math.max(0, (LARGEUR - texte.length()) / 2);
        return " ".repeat(marge) + texte + "\n";
    }
}
