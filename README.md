# Restaurant Manager — Application de gestion de restaurant (JavaFX)

Projet Java — Master 1, 2026.

## Auteurs

- **Kahina AIT KACI ARAB**
- **Assala GUETTAL**
- **Chahd ALOUI**
- **Liliane AOUZENIA**

## Présentation

Restaurant Manager est une application de bureau qui gère les opérations d'un restaurant, de la prise
de commande jusqu'à l'encaissement : plan de salle, commandes par table ou à emporter, encaissement
multi-modes, réservations, stocks, statistiques journalières et prédiction des heures de rush.

## Lancer l'application

Prérequis : **JDK 17 ou plus** (testé avec le JDK 21) et **JavaFX 21**.

### Option 1 — Script Windows
Double-cliquez sur **`lancer.bat`**. Le script compile `src/` dans `build/`, puis lance l'application.
Il utilise les jars JavaFX 21.0.1 du cache Maven (`%USERPROFILE%\.m2`). S'ils sont absents,
téléchargez le SDK JavaFX 21 (https://gluonhq.com/products/javafx/) et définissez la variable
`JAVAFX_HOME` avant de lancer le script :
```
set JAVAFX_HOME=C:\chemin\vers\javafx-sdk-21
lancer.bat
```

### Option 2 — Maven
```
mvn javafx:run
```
Maven télécharge JavaFX automatiquement au premier lancement.

### Option 3 — IntelliJ IDEA
- Ouvrir **`pom.xml`** comme projet Maven, puis exécuter la tâche Maven `javafx:run` ;
- ou lancer directement la classe **`app.Launcher`** (clic droit → Run). Cette classe évite l'erreur
  « JavaFX runtime components are missing ».

## Fonctionnalités

| Onglet | Contenu |
|---|---|
| **Salle & commandes** | Plan de salle (salle, terrasse, bar, emplacements à emporter) avec statuts en temps réel ; ouverture de table liée à la réservation ; prise de commande par suites (la suite 1 part directement en cuisine, les suivantes sont réclamées) ; suppléments, ingrédients retirés, commentaires ; bon de cuisine ; contrôle du stock à la saisie. |
| **Encaissement** | Addition séparée (choix des articles), partage d'un article entre plusieurs personnes, remises et offerts, pavé numérique, paiement mixte (espèces, carte, chèque, ticket restaurant), rendu monnaie (espèces uniquement), ticket de caisse avec TVA 10 % / 20 %, impression. Les paiements partiels sont mémorisés. En cas d'erreur : annulation d'un paiement, correction du mode de paiement, réouverture d'une table encaissée le jour même. |
| **Réservations** | Recherche du client par téléphone, tables proposées selon la capacité et les créneaux libres (2 h), liste d'attente, planning par jour, modification d'une réservation et suivi du statut (arrivée, no-show, annulation). |
| **Stocks** | Ingrédients (réapprovisionnement, seuils d'alerte), disponibilité de la carte, mise en rupture manuelle ou automatique lorsqu'un ingrédient manque. |
| **Statistiques** | Chiffre d'affaires, additions, couverts, ticket moyen, répartition par mode de paiement, CA par heure, articles les plus vendus, **prédiction des heures de rush** (moyenne des mêmes jours de la semaine), affluence moyenne par jour. |
| **Historique** | Toutes les commandes encaissées, recherche, réimpression des tickets, correction des paiements. |
| **Clients** | Fichier clients, nombre de visites, clients fidèles, historique des réservations. |

### Données
Au démarrage, l'application contient la carte, les stocks, quelques clients et réservations, ainsi
que **4 semaines d'historique de démonstration** qui alimentent les statistiques et la prédiction du
rush. Les données sont conservées en mémoire : elles sont réinitialisées à chaque lancement.

## Architecture

```
src/
├── app/         MainApp (application JavaFX), Launcher (lancement depuis l'IDE)
├── controller/  MainController (en-tête + onglets), Salle, Encaissement, Réservation, Stock,
│                Statistiques, Historique, Client, CorrectionPaiementDialog, Ui (composants)
├── enums/       CategoriePlat, ModePaiement, StatutCommande, StatutReservation, StatutTable
├── manager/     RestaurantData (singleton : données et jeu de démonstration)
├── model/       Plat, Ingredient, Recette, Commande, Suite, LigneCommande, Paiement,
│                TableRestaurant, Reservation, Client
├── service/     StatistiqueService (indicateurs, prédiction du rush), TicketService (ticket, bon cuisine)
├── util/        Format (montants et dates au format français)
└── ressources/  style.css, view/main-view.fxml
```
