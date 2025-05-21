package com.ruhr24.schichter.domain;

public class Mitarbeiter {

    private String id; // Eindeutige ID des Mitarbeiters
    private String nachname;
    private String vorname;
    private String ressort;
    private int wochenstunden; // Geplante Wochenarbeitszeit

    public Mitarbeiter() {
    }

    public Mitarbeiter(String id, String nachname, String vorname, String ressort, int wochenstunden) {
        this.id = id;
        this.nachname = nachname;
        this.vorname = vorname;
        this.ressort = ressort;
        this.wochenstunden = wochenstunden;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(String nachname) {
        this.nachname = nachname;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getRessort() {
        return ressort;
    }

    public void setRessort(String ressort) {
        this.ressort = ressort;
    }

    public int getWochenstunden() {
        return wochenstunden;
    }

    public void setWochenstunden(int wochenstunden) {
        this.wochenstunden = wochenstunden;
    }

    // Optional: Eine Methode, um den vollständigen Namen zu bekommen
    public String getVollerName() {
        return vorname + " " + nachname;
    }

    @Override
    public String toString() {
        return "Mitarbeiter{" +
                "id='" + id + '\'' +
                ", nachname='" + nachname + '\'' +
                ", vorname='" + vorname + '\'' +
                ", ressort='" + ressort + '\'' +
                ", wochenstunden=" + wochenstunden +
                '}';
    }
}