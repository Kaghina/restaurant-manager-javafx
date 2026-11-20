package model;

public class Client {
    private static int compteur = 1;

    private final int id;
    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private int nombreVisites;
    private String notes = "";

    public Client(String nom, String prenom, String telephone, String email) {
        this.id = compteur++;
        this.nom = nom.toUpperCase();
        this.prenom = prenom;
        this.telephone = telephone;
        this.email = email == null ? "" : email;
    }

    public void incrementerVisites() { nombreVisites++; }
    public String getNomComplet()    { return prenom + " " + nom; }
    public boolean estFidele()       { return nombreVisites >= 5; }

    public int getId()                 { return id; }
    public String getNom()             { return nom; }
    public void setNom(String nom)     { this.nom = nom.toUpperCase(); }
    public String getPrenom()          { return prenom; }
    public void setPrenom(String p)    { this.prenom = p; }
    public String getTelephone()       { return telephone; }
    public void setTelephone(String t) { this.telephone = t; }
    public String getEmail()           { return email; }
    public void setEmail(String e)     { this.email = e == null ? "" : e; }
    public int getNombreVisites()      { return nombreVisites; }
    public void setNombreVisites(int n){ this.nombreVisites = Math.max(0, n); }
    public String getNotes()           { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }

    @Override
    public String toString() { return getNomComplet() + " — " + telephone; }
}
