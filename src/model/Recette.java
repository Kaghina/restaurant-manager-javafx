package model;

import util.Format;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Quantité de chaque ingrédient nécessaire pour UNE portion d'un plat. */
public class Recette {
    private final Map<Ingredient, Double> ingredients = new LinkedHashMap<>();

    public Recette ajouter(Ingredient ingredient, double quantiteParPortion) {
        ingredients.merge(ingredient, quantiteParPortion, Double::sum);
        return this;
    }

    /** Vrai si le stock permet de préparer {@code portions} portions. */
    public boolean estRealisable(int portions) {
        return ingredients.entrySet().stream()
                .allMatch(e -> e.getKey().getQuantiteStock() + 1e-9 >= e.getValue() * portions);
    }

    public void consommer(int portions) {
        ingredients.forEach((ingredient, qte) -> ingredient.consommer(qte * portions));
    }

    public Map<Ingredient, Double> getIngredients() { return Collections.unmodifiableMap(ingredients); }

    public String getDescription() {
        return ingredients.entrySet().stream()
                .map(e -> Format.nombre(e.getValue()) + " " + e.getKey().getUnite() + " " + e.getKey().getNom())
                .collect(Collectors.joining(", "));
    }
}
