package com.ruhr24.schichter.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class Schicht {

    private UUID id;
    private LocalDate datum;
    private LocalTime startZeit;
    private LocalTime endZeit;
    private String ressortBedarf;
    private int benötigteMitarbeiter;

    // Standard-Konstruktor
    public Schicht() {
    }

    // Neuer Konstruktor mit UUID (für deine Verwendung im PlanningController)
    public Schicht(UUID id, LocalDate datum, LocalTime startZeit, LocalTime endZeit,
                   String ressortBedarf, int benötigteMitarbeiter) {
        this.id = id;
        this.datum = datum;
        this.startZeit = startZeit;
        this.endZeit = endZeit;
        this.ressortBedarf = ressortBedarf;
        this.benötigteMitarbeiter = benötigteMitarbeiter;
    }

    // Getter & Setter
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getDatum() {
        return datum;
    }

    public void setDatum(LocalDate datum) {
        this.datum = datum;
    }

    public LocalTime getStartZeit() {
        return startZeit;
    }

    public void setStartZeit(LocalTime startZeit) {
        this.startZeit = startZeit;
    }

    public LocalTime getEndZeit() {
        return endZeit;
    }

    public void setEndZeit(LocalTime endZeit) {
        this.endZeit = endZeit;
    }

    public String getRessortBedarf() {
        return ressortBedarf;
    }

    public void setRessortBedarf(String ressortBedarf) {
        this.ressortBedarf = ressortBedarf;
    }

    public int getBenötigteMitarbeiter() {
        return benötigteMitarbeiter;
    }

    public void setBenötigteMitarbeiter(int benötigteMitarbeiter) {
        this.benötigteMitarbeiter = benötigteMitarbeiter;
    }

    public double getDauerInStunden() {
        if (startZeit == null || endZeit == null) {
            return 0.0;
        }
        // Behandelt Schichten, die über Mitternacht gehen könnten, falls dies ein Szenario ist
        long minutes = ChronoUnit.MINUTES.between(startZeit, endZeit);
        // Wenn die Endzeit vor der Startzeit liegt, bedeutet das, sie geht über Mitternacht.
        if (minutes < 0) {
            minutes += (24 * 60); // Füge 24 Stunden hinzu
        }
        return (double) minutes / 60.0;
    }    

    @Override
    public String toString() {
        return "Schicht{" +
                "id=" + id +
                ", datum=" + datum +
                ", startZeit=" + startZeit +
                ", endZeit=" + endZeit +
                ", ressortBedarf='" + ressortBedarf + '\'' +
                ", benötigteMitarbeiter=" + benötigteMitarbeiter +
                '}';
    }
}
