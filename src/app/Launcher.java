package app;

/**
 * Point d'entrée "classpath" : permet de lancer l'application depuis l'IDE (Run sur cette classe)
 * sans configurer de --module-path. Une classe qui n'hérite pas d'Application évite l'erreur
 * "JavaFX runtime components are missing".
 */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
