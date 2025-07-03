package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty; // WICHTIG: Dieser Import
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore; // RICHTIG!
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.domain.lookup.PlanningId; // Für die @PlanningId in SchichtPlan

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
//import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;


@PlanningSolution
public class SchichtPlan {

    @PlanningId // OptaPlanner benötigt eine ID für die Lösung selbst
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

    /*
    @ProblemFactCollectionProperty
    private List<Schicht> alleSchichten; // Eine Liste aller einzelnen Schicht-Objekte im Problem
    */

    @PlanningEntityCollectionProperty
    private List<Arbeitsmuster> arbeitsmusterList;

    @PlanningScore
    private HardSoftLongScore score;

    // Map zur Aggregation der tatsächlich geplanten Stunden pro Mitarbeiter
    private Map<Mitarbeiter, Double> tatsaechlichGeplanteStundenProMitarbeiter;

    // Set zur Speicherung der öffentlichen Feiertage im Planungszeitraum
    @ProblemFactCollectionProperty
    private Set<LocalDate> publicHolidays;


    // 1. Angepasster No-Arg-Konstruktor
    public SchichtPlan() {
        this.mitarbeiterList = new ArrayList<>();
        this.arbeitsmusterList = new ArrayList<>();
        this.publicHolidays = new HashSet<>();
    }

    // 2. Der einzige benötigte Konstruktor für das neue, einfache Modell
    public SchichtPlan(UUID id, LocalDate von, LocalDate bis, String ressort,
                       List<Mitarbeiter> mitarbeiterList, List<Arbeitsmuster> arbeitsmusterList, Set<LocalDate> publicHolidays) {
        this.id = id;
        this.von = von;
        this.bis = bis;
        this.mitarbeiterList = mitarbeiterList;
        this.arbeitsmusterList = arbeitsmusterList;
        this.publicHolidays = publicHolidays;
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

    /*
    // NEU: Getter und Setter für die Liste aller Schichten
    public List<Schicht> getAlleSchichten() {
        return alleSchichten;
    }

    public void setAlleSchichten(List<Schicht> alleSchichten) {
        this.alleSchichten = alleSchichten;
    } */

    public List<Arbeitsmuster> getArbeitsmusterList() {
        return arbeitsmusterList;
    }

    public void setArbeitsmusterList(List<Arbeitsmuster> arbeitsmusterList) {
        this.arbeitsmusterList = arbeitsmusterList;
    }

    public HardSoftLongScore getScore() {
        return score;
    }

    public void setScore(HardSoftLongScore score) {
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
               //", alleSchichten=" + (alleSchichten != null ? alleSchichten.size() : 0) + " Schichten" + // NEU: Ausgabe
               ", arbeitsmusterList=" + (arbeitsmusterList != null ? arbeitsmusterList.size() : 0) + " Arbeitsmuster" +
               ", score=" + score +
               ", publicHolidays=" + (publicHolidays != null ? publicHolidays.size() : 0) + " Feiertage" +
               '}';
    }
}
