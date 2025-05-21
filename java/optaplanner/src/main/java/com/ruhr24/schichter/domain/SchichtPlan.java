package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID; // WICHTIG: Import für UUID hinzufügen!

// NEUE IMPORTS FÜR JSON/DATUMSFORMATIERUNG (Diese sind für die DTO-Verarbeitung nützlich, aber nicht die Ursache des aktuellen Fehlers)
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;


@PlanningSolution
public class SchichtPlan {

    private UUID id; // NEU HINZUGEFÜGT: Eine eindeutige ID für den Schichtplan

    // Felder für den Planungszeitraum und das Ressort
    @JsonFormat(pattern = "yyyy-MM-dd") // Für die Deserialisierung von JSON-Strings
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // Für Spring, wenn es als RequestParam/Path-Variable kommt (gut für Konsistenz)
    private LocalDate von; // Startdatum des Planungszeitraums

    @JsonFormat(pattern = "yyyy-MM-dd") // Für die Deserialisierung von JSON-Strings
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // Für Spring, wenn es als RequestParam/Path-Variable kommt
    private LocalDate bis; // Enddatum des Planungszeitraums

    private String ressort; // Das Ressort, für das geplant wird (optional, je nach Anwendungsfall)

    @ProblemFactCollectionProperty
    private List<Schicht> schichtList;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "mitarbeiterRange") // @ValueRangeProvider hierher verschoben
    private List<Mitarbeiter> mitarbeiterList;

    @PlanningEntityCollectionProperty
    private List<Zuteilung> zuteilungList;

    @PlanningScore
    private HardSoftScore score;

    // --- NEU HINZUGEFÜGTE Getter und Setter für die 'id' ---
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    // --- Bestehende Getter und Setter (Rest des Codes bleibt gleich) ---
    public LocalDate getVon() {
        return von;
    }

    public void setVon(LocalDate von) {
        this.von = von;
    }

    public LocalDate getBis() {
        return bis;
    }

    public void setBis(LocalDate bis) {
        this.bis = bis;
    }

    public String getRessort() {
        return ressort;
    }

    public void setRessort(String ressort) {
        this.ressort = ressort;
    }

    public List<Schicht> getSchichtList() {
        return schichtList;
    }

    public void setSchichtList(List<Schicht> schichtList) {
        this.schichtList = schichtList;
    }

    public List<Mitarbeiter> getMitarbeiterList() {
        return mitarbeiterList;
    }

    public void setMitarbeiterList(List<Mitarbeiter> mitarbeiterList) {
        this.mitarbeiterList = mitarbeiterList;
    }

    public List<Zuteilung> getZuteilungList() {
        return zuteilungList;
    }

    public void setZuteilungList(List<Zuteilung> zuteilungList) {
        this.zuteilungList = zuteilungList;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    // Die redundante Methode getMitarbeiterListe() wurde entfernt,
    // da getMitarbeiterList() bereits existiert und für @ValueRangeProvider verwendet wird.


    // Optional: toString-Methode für besseres Logging
    @Override
    public String toString() {
        return "SchichtPlan{" +
               "id=" + id + // ID hier für den toString-Output hinzufügen
               ", von=" + von +
               ", bis=" + bis +
               ", ressort='" + ressort + '\'' +
               ", schichtList=" + (schichtList != null ? schichtList.size() : 0) + " Schichten" +
               ", mitarbeiterList=" + (mitarbeiterList != null ? mitarbeiterList.size() : 0) + " Mitarbeiter" +
               ", zuteilungList=" + (zuteilungList != null ? zuteilungList.size() : 0) + " Zuteilungen" +
               ", score=" + score +
               '}';
    }
}