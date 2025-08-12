package com.ruhr24.schichter.domain;

import com.ruhr24.schichter.solver.ArbeitsmusterDifficultyComparator;
import com.ruhr24.schichter.solver.MitarbeiterStrengthComparator;
import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import java.util.List;
import java.util.UUID;

@PlanningEntity(difficultyComparatorClass = ArbeitsmusterDifficultyComparator.class)
public class Arbeitsmuster {

    @PlanningId
    private UUID id;
    private String musterTyp;
    private int wochenstunden;
    private int wocheImJahr; // Wichtiges Feld für Wochen-Logik
    private List<Schicht> schichten;

    @PlanningVariable(valueRangeProviderRefs = {"mitarbeiterRange"}, strengthComparatorClass = MitarbeiterStrengthComparator.class)
    private Mitarbeiter mitarbeiter;

    @PlanningPin
    private boolean pinned = false; 

    // OptaPlanner benötigt einen leeren Konstruktor
    public Arbeitsmuster() {}

    /**
     * Dies ist der korrekte Konstruktor, den dein MusterGenerator aufruft.
     * Er nimmt den Typ, die Stunden, die Wochennummer und die Liste der Schichten entgegen.
     */
    public Arbeitsmuster(String musterTyp, int wochenstunden, int wocheImJahr, List<Schicht> schichten) {
        this.id = UUID.randomUUID();
        this.musterTyp = musterTyp;
        this.wochenstunden = wochenstunden;
        this.wocheImJahr = wocheImJahr;
        this.schichten = schichten;
    }

    // --- Getter und Setter ---

    public UUID getId() {
        return id;
    }

    public String getMusterTyp() {
        return musterTyp;
    }

    public int getWochenstunden() {
        return wochenstunden;
    }

    public int getWocheImJahr() {
        return wocheImJahr;
    }

    public List<Schicht> getSchichten() {
        return schichten;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return "Arbeitsmuster{" +
                "id=" + id +
                ", musterTyp='" + musterTyp + '\'' +
                ", wochenstunden=" + wochenstunden +
                ", wocheImJahr=" + wocheImJahr +
                ", schichten=" + (schichten != null ? schichten.size() : 0) +
                ", mitarbeiter=" + (mitarbeiter != null ? mitarbeiter.getNachname() : "unassigned") +
                '}';
    }
}