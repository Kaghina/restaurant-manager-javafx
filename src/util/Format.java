package util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatage homogène des montants, quantités et dates (format français). */
public final class Format {
    private static final Locale FR = Locale.FRANCE;
    public static final DateTimeFormatter HEURE      = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE       = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Format() { }

    /** 12.5 → "12,50 €" */
    public static String euros(double montant) {
        return String.format(FR, "%.2f €", arrondi(montant));
    }

    /** 0.25 → "0,25" ; 2.0 → "2" */
    public static String nombre(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(FR, "%.2f", v).replaceAll("0+$", "");
    }

    public static String heure(LocalDateTime dt)     { return dt.format(HEURE); }
    public static String dateHeure(LocalDateTime dt) { return dt.format(DATE_HEURE); }

    public static double arrondi(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Lit un nombre saisi par l'utilisateur ("12,5" ou "12.5"). Retourne NaN si invalide. */
    public static double lireNombre(String texte) {
        if (texte == null) return Double.NaN;
        try {
            return Double.parseDouble(texte.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
