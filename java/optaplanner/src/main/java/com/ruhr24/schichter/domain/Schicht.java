package com.ruhr24.schichter.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public class Schicht {

    private UUID id;
    private LocalDate datum;
    private LocalTime startZeit;
    private LocalTime endZeit;
    private String ressortBedarf; // Bleibt String, da wir den DisplayValue speichern
    private int benoetigteMitarbeiter;
    private String schichtTyp; // Bleibt String, da wir den DisplayValue speichern
    private boolean isHolidayShift; // Ist dies eine Feiertagsschicht?

    // No-arg Konstruktor für OptaPlanner und andere Frameworks
    public Schicht() {
    }

    // Konstruktor mit allen Feldern
    public Schicht(UUID id, LocalDate datum, LocalTime startZeit, LocalTime endZeit,
                   String ressortBedarf, int benoetigteMitarbeiter, String schichtTyp, boolean isHolidayShift) {
        this.id = id;
        this.datum = datum;
        this.startZeit = startZeit;
        this.endZeit = endZeit;
        this.ressortBedarf = ressortBedarf;
        this.benoetigteMitarbeiter = benoetigteMitarbeiter;
        this.schichtTyp = schichtTyp;
        this.isHolidayShift = isHolidayShift;
    }

    // Zusätzlicher Konstruktor für den Generator (hier wieder mit String-Parametern)
    // Dieser wird vom SchichtBlockGenerator benötigt, da er die Strings übergibt.
    public Schicht(LocalDate datum, LocalTime startZeit, LocalTime endZeit,
                   String schichtTyp, String ressortBedarf) { // <-- WIEDERHERGESTELLT!
        this(UUID.randomUUID(), datum, startZeit, endZeit, ressortBedarf, 1, schichtTyp, false); // Standardmäßig 1 benötigter MA, kein Feiertag
    }

    // Der Konstruktor, der Enums direkt als Parameter akzeptiert (optional, aber gute Praxis)
    // Diesen kannst du nutzen, wenn du ihn an anderen Stellen im Code brauchst
    // oder wenn der SchichtBlockGenerator später umgestellt wird.
    public Schicht(LocalDate datum, LocalTime startZeit, LocalTime endZeit,
                   SchichtTyp schichtTypEnum, Ressort ressortBedarfEnum) {
        this(UUID.randomUUID(), datum, startZeit, endZeit,
             ressortBedarfEnum.getDisplayValue(),
             1,
             schichtTypEnum.getDisplayValue(),
             false);
    }


    // --- Getter und Setter ---
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

    public int getBenoetigteMitarbeiter() {
        return benoetigteMitarbeiter;
    }

    public void setBenoetigteMitarbeiter(int benoetigteMitarbeiter) {
        this.benoetigteMitarbeiter = benoetigteMitarbeiter;
    }

    public String getSchichtTyp() {
        return schichtTyp;
    }

    public void setSchichtTyp(String schichtTyp) {
        this.schichtTyp = schichtTyp;
    }

    public boolean isHolidayShift() {
        return isHolidayShift;
    }

    public void setHolidayShift(boolean holidayShift) {
        isHolidayShift = holidayShift;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schicht schicht = (Schicht) o;
        return benoetigteMitarbeiter == schicht.benoetigteMitarbeiter &&
               isHolidayShift == schicht.isHolidayShift &&
               Objects.equals(id, schicht.id) &&
               Objects.equals(datum, schicht.datum) &&
               Objects.equals(startZeit, schicht.startZeit) &&
               Objects.equals(endZeit, schicht.endZeit) &&
               Objects.equals(ressortBedarf, schicht.ressortBedarf) &&
               Objects.equals(schichtTyp, schicht.schichtTyp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, datum, startZeit, endZeit, ressortBedarf, benoetigteMitarbeiter, schichtTyp, isHolidayShift);
    }

    @Override
    public String toString() {
        return "Schicht{" +
               "datum=" + datum +
               ", startZeit=" + startZeit +
               ", endZeit=" + endZeit +
               ", ressortBedarf='" + ressortBedarf + '\'' +
               ", schichtTyp='" + schichtTyp + '\'' +
               '}';
    }
}