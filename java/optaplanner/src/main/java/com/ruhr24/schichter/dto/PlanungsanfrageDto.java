package com.ruhr24.schichter.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruhr24.schichter.domain.Mitarbeiter;

// Ein DTO (Data Transfer Object) um die eingehenden Daten zu empfangen
public class PlanungsanfrageDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate von;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bis;

    private String ressort;

    private List<Mitarbeiter> mitarbeiterList; // Mitarbeiter werden direkt im DTO empfangen

    // Konstruktor (optional, aber hilfreich)
    public PlanungsanfrageDto() {
    }

    public PlanungsanfrageDto(LocalDate von, LocalDate bis, String ressort, List<Mitarbeiter> mitarbeiterList) {
        this.von = von;
        this.bis = bis;
        this.ressort = ressort;
        this.mitarbeiterList = mitarbeiterList;
    }

    // Getter und Setter
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

    @Override
    public String toString() {
        return "PlanungsanfrageDto{" +
               "von=" + von +
               ", bis=" + bis +
               ", ressort='" + ressort + '\'' +
               ", mitarbeiterList=" + (mitarbeiterList != null ? mitarbeiterList.size() : 0) + " Mitarbeiter" +
               '}';
    }
}