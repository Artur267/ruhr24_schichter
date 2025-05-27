package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;


@PlanningSolution
public class SchichtPlan {

    private UUID id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate von;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bis;

    private String ressort;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "mitarbeiterRange")
    private List<Mitarbeiter> mitarbeiterList;

    // HIER ist die @PlanningEntityCollectionProperty Annotation richtig platziert: NUR auf dem Feld
    @PlanningEntityCollectionProperty
    private List<SchichtBlock> schichtBlockList; // Die Liste der zu planenden SchichtBlöcke

    @PlanningScore
    private HardSoftScore score;

    // Map zur Aggregation der tatsächlich geplanten Stunden pro Mitarbeiter
    private Map<Mitarbeiter, Double> tatsaechlichGeplanteStundenProMitarbeiter;

    // Set zur Speicherung der öffentlichen Feiertage im Planungszeitraum
    @ProblemFactCollectionProperty
    private Set<LocalDate> publicHolidays;


    // Konstruktor, um die Maps und Listen zu initialisieren
    public SchichtPlan() {
        this.tatsaechlichGeplanteStundenProMitarbeiter = new HashMap<>();
        this.publicHolidays = new HashSet<>();
        this.schichtBlockList = new ArrayList<>(); // Initialisiere die neue Liste
    }

    // --- Getter und Setter ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public List<Mitarbeiter> getMitarbeiterList() {
        return mitarbeiterList;
    }

    public void setMitarbeiterList(List<Mitarbeiter> mitarbeiterList) {
        this.mitarbeiterList = mitarbeiterList;
    }

    // WICHTIG: Hier ist KEINE @PlanningEntityCollectionProperty Annotation mehr!
    public List<SchichtBlock> getSchichtBlockList() {
        return schichtBlockList;
    }

    public void setSchichtBlockList(List<SchichtBlock> schichtBlockList) {
        this.schichtBlockList = schichtBlockList;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public Map<Mitarbeiter, Double> getTatsaechlichGeplanteStundenProMitarbeiter() {
        return tatsaechlichGeplanteStundenProMitarbeiter;
    }

    public void setTatsaechlichGeplanteStundenProMitarbeiter(Map<Mitarbeiter, Double> tatsaechlichGeplanteStundenProMitarbeiter) {
        this.tatsaechlichGeplanteStundenProMitarbeiter = tatsaechlichGeplanteStundenProMitarbeiter;
    }

    public Set<LocalDate> getPublicHolidays() {
        return publicHolidays;
    }

    public void setPublicHolidays(Set<LocalDate> publicHolidays) {
        this.publicHolidays = publicHolidays;
    }

    @Override
    public String toString() {
        return "SchichtPlan{" +
               "id=" + id +
               ", von=" + von +
               ", bis=" + bis +
               ", ressort='" + ressort + '\'' +
               ", mitarbeiterList=" + (mitarbeiterList != null ? mitarbeiterList.size() : 0) + " Mitarbeiter" +
               ", schichtBlockList=" + (schichtBlockList != null ? schichtBlockList.size() : 0) + " SchichtBlöcke" +
               ", score=" + score +
               ", publicHolidays=" + (publicHolidays != null ? publicHolidays.size() : 0) + " Feiertage" +
               '}';
    }
}