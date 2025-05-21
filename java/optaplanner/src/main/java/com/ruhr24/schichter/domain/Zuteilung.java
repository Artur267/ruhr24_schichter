package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId; // WICHTIG: Import für @PlanningId hinzufügen!
import org.optaplanner.core.api.domain.variable.PlanningVariable;
import org.optaplanner.core.api.domain.variable.PlanningVariableReference; // Wichtig für @PlanningVariable(nullable = false)

import java.util.UUID; // WICHTIG: Import für UUID hinzufügen!

// Importiere HardSoftScore (oder den von dir verwendeten Score-Typ),
// wenn du später Constraints in dieser Klasse verwenden willst.
// import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;


@PlanningEntity // Diese Klasse ist ein Objekt, das OptaPlanner "plant" oder verändert
public class Zuteilung {

    @PlanningId // Eine eindeutige ID für das Planning Entity (wichtig für OptaPlanner)
    private UUID id; // NEU HINZUGEFÜGT: Eine eindeutige ID für die Zuteilung

    // Schicht ist ein "Problem Fact", der nicht vom Solver verändert wird.
    // Er ist eine feste Vorgabe für diese Zuteilung.
    private Schicht schicht;

    // Mitarbeiter ist die "Planning Variable", die OptaPlanner zuweisen wird.
    private Mitarbeiter mitarbeiter;

    // Default-Konstruktor ist wichtig für Frameworks wie Spring und OptaPlanner
    public Zuteilung() {}

    // Konstruktor, um eine Zuteilung für eine bestimmte Schicht zu erstellen
    public Zuteilung(Schicht schicht) {
        this.schicht = schicht;
        this.id = UUID.randomUUID(); // Setze hier eine ID, wenn nur mit Schicht initialisiert
    }

    // Konstruktor für Initialisierung (falls nötig), aber OptaPlanner setzt den Mitarbeiter
    public Zuteilung(Schicht schicht, Mitarbeiter mitarbeiter) {
        this.schicht = schicht;
        this.mitarbeiter = mitarbeiter;
        this.id = UUID.randomUUID(); // Setze hier eine ID
    }

    // --- NEU HINZUGEFÜGTE Getter und Setter für die 'id' ---
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    // Die Schicht ist ein Problem Fact und sollte NICHT als PlanningVariable annotiert werden.
    public Schicht getSchicht() {
        return schicht;
    }

    public void setSchicht(Schicht schicht) {
        this.schicht = schicht;
    }

    // Der Mitarbeiter ist die Planning Variable. OptaPlanner weist hier einen Wert zu.
    @PlanningVariable(valueRangeProviderRefs = {"mitarbeiterRange"},
                      nullable = true) // Setze dies auf `true`, wenn eine Schicht unbesetzt bleiben kann,
                                       // oder auf `false`, wenn jede Schicht einen Mitarbeiter braucht.
                                       // Wenn `false` und keine Lösung gefunden wird, gibt es einen Fehler.
                                       // Für den Anfang ist `true` sicherer.
    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }

    @Override
    public String toString() {
        // Verbessere die toString() für bessere Lesbarkeit
        String schichtDetails = (schicht != null) ? schicht.getDatum() + " " + schicht.getStartZeit() + "-" + schicht.getEndZeit() + " (" + schicht.getRessortBedarf() + ")" : "N/A";
        String mitarbeiterName = (mitarbeiter != null) ? mitarbeiter.getVorname() + " " + mitarbeiter.getNachname() : "Unbesetzt"; // Geändert auf getVorname() + getNachname()
        return "Zuteilung{" +
                "id=" + id + // ID hier für den toString-Output hinzufügen
                ", Schicht=" + schichtDetails +
                ", Mitarbeiter=" + mitarbeiterName +
                '}';
    }
}