package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.domain.solution.PlanningEntityCollectionProperty;
import org.optaplanner.core.api.domain.solution.ProblemFactCollectionProperty;
import org.optaplanner.core.api.domain.solution.ProblemFactProperty;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.domain.solution.PlanningScore;
import org.optaplanner.core.api.domain.valuerange.ValueRangeProvider;
import org.optaplanner.core.api.domain.lookup.PlanningId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;


@PlanningSolution
public class SchichtPlan {

    @PlanningId
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
    private List<Mitarbeiter> mitarbeiterList = new ArrayList<>();

    // Die Liste der Abwesenheiten. Wird direkt initialisiert, um null zu vermeiden.
    private List<Abwesenheit> alleAbwesenheiten = new ArrayList<>();

    @ProblemFactCollectionProperty
    private List<MitarbeiterFreiWunsch> freiWuensche = new ArrayList<>();

    @PlanningEntityCollectionProperty
    private List<Arbeitsmuster> arbeitsmusterList = new ArrayList<>();

    @PlanningScore
    private HardSoftLongScore score;

    private Map<Mitarbeiter, Double> tatsaechlichGeplanteStundenProMitarbeiter;

    @ProblemFactCollectionProperty
    private Set<LocalDate> publicHolidays = new HashSet<>();


    // No-Arg-Konstruktor für OptaPlanner/JPA
    public SchichtPlan() {
        // Alle Listen werden bereits bei ihrer Deklaration initialisiert.
    }

    // KORRIGIERTER Konstruktor, der jetzt auch die Abwesenheiten entgegennimmt
    public SchichtPlan(UUID id, LocalDate von, LocalDate bis, String ressort,
                       List<Mitarbeiter> mitarbeiterList, List<Arbeitsmuster> arbeitsmusterList,
                       Set<LocalDate> publicHolidays, List<Abwesenheit> alleAbwesenheiten) {
        this.id = id;
        this.von = von;
        this.bis = bis;
        this.ressort = ressort;
        this.mitarbeiterList = mitarbeiterList != null ? mitarbeiterList : Collections.emptyList();
        this.arbeitsmusterList = arbeitsmusterList != null ? arbeitsmusterList : Collections.emptyList();
        this.publicHolidays = publicHolidays != null ? publicHolidays : Collections.emptySet();
        this.alleAbwesenheiten = alleAbwesenheiten != null ? alleAbwesenheiten : Collections.emptyList();
    }


    // --- Getter und Setter ---

    // Die Annotation auf dem Getter ist korrekt und wird von OptaPlanner erkannt.
    @ProblemFactCollectionProperty
    public List<Abwesenheit> getAlleAbwesenheiten() {
        return alleAbwesenheiten;
    }

    public void setAlleAbwesenheiten(List<Abwesenheit> alleAbwesenheiten) {
        this.alleAbwesenheiten = alleAbwesenheiten;
    }

    public List<MitarbeiterFreiWunsch> getFreiWuensche() {
        return freiWuensche;
    }

    public void setFreiWuensche(List<MitarbeiterFreiWunsch> freiWuensche) {
        this.freiWuensche = freiWuensche;
    }

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
}
