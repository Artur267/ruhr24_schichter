// src/main/java/com/ruhr24.schichter.domain/Schicht.java
package com.ruhr24.schichter.domain;
import com.ruhr24.schichter.solver.MitarbeiterStrengthComparator;
import com.ruhr24.schichter.solver.SchichtDifficultyComparator;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.Objects;
import java.util.UUID;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import com.fasterxml.jackson.annotation.JsonIgnore; // Wichtig für @JsonIgnore

public class Schicht {
    @PlanningId
    private UUID id; // ID ist jetzt UUID, um Konsistenz mit SchichtPlan und Generator zu gewährleisten
    private LocalDate datum;
    private LocalTime startZeit;
    private LocalTime endZeit;
    private String ressortBedarf;
    private int benoetigteMitarbeiter;
    private String schichtTyp;
    private boolean isHolidayShift;
    private Mitarbeiter mitarbeiter; // Der zugewiesene Mitarbeiter

    // Referenz zum übergeordneten SchichtBlock
    @JsonIgnore // Verhindert Endlosschleifen bei der JSON-Serialisierung
    private SchichtBlock schichtBlock;

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
        this.mitarbeiter = null;
        this.schichtBlock = null;
    }

    public long getDurationInMinutes() {
        if (getStartDateTime() != null && getEndDateTime() != null) {
            return Duration.between(getStartDateTime(), getEndDateTime()).toMinutes();
        }
        return 0;
    }
    
    /**
     * Berechnet die reine Arbeitszeit dieser einzelnen Schicht in Minuten unter Berücksichtigung von Pausen.
     * Logik basierend auf SchichtTyp.
     * @return Arbeitszeit in Minuten.
     */
    public long getArbeitszeitInMinuten() {
        if (startZeit == null || endZeit == null) {
            return 0; // Oder entsprechende Fehlerbehandlung
        }

        // Reine Dauer der Schicht (kann über Mitternacht gehen, wird hier nicht separat gehandhabt,
        // da deine Logik unten nur den SchichtTyp berücksichtigt)
        long rawDurationMinutes = Duration.between(startZeit, endZeit).toMinutes();
        // Falls die Schicht über Mitternacht geht (z.B. 22:00-06:00), ist die reine Dauer negativ oder sehr klein.
        // OptaPlanner sollte mit korrekten Zeitspannen arbeiten, also ist das hier eine Vereinfachung
        // basierend auf den von dir definierten Schichttypen.
        if (rawDurationMinutes < 0) { // Korrigiere Dauer bei Übernachtschichten
             rawDurationMinutes += 24 * 60; // Füge 24 Stunden hinzu
        }

        // Berechnung der Arbeitszeit basierend auf SchichtTyp (wie von dir definiert)
        // HINWEIS: Hier wird davon ausgegangen, dass SchichtTyp.fromDisplayValue(schichtTyp) existiert
        // oder du direkt String-Vergleiche machst, wie im SchichtBlockGenerator.
        // Da du SchichtTyp als String speicherst, nutze ich String-Vergleiche.
        switch (this.schichtTyp) {
            case "8-Stunden-Dienst":
            case "Kerndienst":
            case "CvD Frühdienst":
            case "CvD Spätdienst":
            case "Standard Wochenend-Dienst":
            case "Weiterbildung":
            case "Verwaltung":
                return 8 * 60; // 8 Stunden reine Arbeitszeit
            case "7-Stunden-Schicht":
                return 7 * 60;
            case "6-Stunden-Dienst":
                return 6 * 60;
            case "5-Stunden-Dienst":
                return 5 * 60;
            case "4-Stunden-Dienst (Früh)":
            case "4-Stunden-Dienst (Spät)":
                return 4 * 60;
            default:
                // Fallback: Wenn ein unbekannter SchichtTyp auftaucht, nimm die reine Dauer
                // Optional: - 30 Minuten Pause, wenn Dauer > 6 Stunden
                if (rawDurationMinutes > (6 * 60)) {
                    return rawDurationMinutes - 30; // 30 Minuten Pause
                }
                return rawDurationMinutes;
        }
    }


    // --- Getter und Setter ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDate getDatum() { return datum; }
    public void setDatum(LocalDate datum) { this.datum = datum; }
    public LocalTime getStartZeit() { return startZeit; }
    public void setStartZeit(LocalTime startZeit) { this.startZeit = startZeit; }
    public LocalTime getEndZeit() { return endZeit; }
    public void setEndZeit(LocalTime endZeit) { this.endZeit = endZeit; }
    public LocalDateTime getStartDateTime() {
        if (datum == null || startZeit == null) { return null; }
        return LocalDateTime.of(datum, startZeit);
    }
    public LocalDateTime getEndDateTime() {
        if (datum == null || endZeit == null) { return null; }
        // Berücksichtigt Schichten, die über Mitternacht gehen (wenn Endzeit vor Startzeit liegt)
        LocalDateTime end = LocalDateTime.of(datum, endZeit);
        if (end.isBefore(LocalDateTime.of(datum, startZeit))) { // Vergleiche mit dem StartDateTime der Schicht
            end = end.plusDays(1);
        }
        return end;
    }
    public String getRessortBedarf() { return ressortBedarf; }
    public void setRessortBedarf(String ressortBedarf) { this.ressortBedarf = ressortBedarf; }
    public int getBenoetigteMitarbeiter() { return benoetigteMitarbeiter; }
    public void setBenoetigteMitarbeiter(int benoetigteMitarbeiter) { this.benoetigteMitarbeiter = benoetigteMitarbeiter; }
    public String getSchichtTyp() { return schichtTyp; }
    public void setSchichtTyp(String schichtTyp) { this.schichtTyp = schichtTyp; }
    public boolean isHolidayShift() { return isHolidayShift; }
    public void setHolidayShift(boolean holidayShift) { isHolidayShift = holidayShift; }

    @PlanningVariable(
        valueRangeProviderRefs = {"mitarbeiterRange"},
        strengthComparatorClass = MitarbeiterStrengthComparator.class // DIESE ZEILE IST DER FIX
    )
    public Mitarbeiter getMitarbeiter() { return mitarbeiter; }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) { this.mitarbeiter = mitarbeiter; }

    @JsonIgnore // Wichtig, um JSON-Endlosschleifen zu vermeiden
    public SchichtBlock getSchichtBlock() { return schichtBlock; }
    public void setSchichtBlock(SchichtBlock schichtBlock) { this.schichtBlock = schichtBlock; }

    /**
     * Prüft, ob diese Schicht eine CvD-Wochenendschicht ist.
     * Basierend auf SchichtTyp und Wochentag.
     */
    public boolean isCvdWochenendShift() {
        if (schichtTyp == null || datum == null) {
            return false;
        }
        boolean isCvdType = schichtTyp.equals(SchichtTyp.CVD_FRUEHDIENST.getDisplayValue()) ||
                            schichtTyp.equals(SchichtTyp.CVD_SPAETDIENST.getDisplayValue()) ||
                            // ACHTUNG: Hier werden die korrekten SchichtTypen für einzelne Wochenendschichten verwendet
                            schichtTyp.equals(SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue()) ||
                            schichtTyp.equals(SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue());
        boolean isWeekend = datum.getDayOfWeek() == DayOfWeek.SATURDAY || datum.getDayOfWeek() == DayOfWeek.SUNDAY;
        return isCvdType && isWeekend;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schicht schicht = (Schicht) o;
        return Objects.equals(id, schicht.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Schicht{" +
               "id=" + id +
               ", datum=" + datum +
               ", startZeit=" + startZeit +
               ", endZeit=" + endZeit +
               ", ressortBedarf='" + ressortBedarf + '\'' +
               ", schichtTyp='" + schichtTyp + '\'' +
               ", zugewiesenerMitarbeiter=" + (mitarbeiter != null ? mitarbeiter.getNachname() : "unassigned") +
               ", arbeitszeit=" + String.format("%.2f", getArbeitszeitInMinuten() / 60.0) + "h" +
               '}';
    }
}
