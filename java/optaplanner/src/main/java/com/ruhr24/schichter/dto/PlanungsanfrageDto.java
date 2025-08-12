package com.ruhr24.schichter.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruhr24.schichter.domain.Abwesenheit; // WICHTIG: Import hinzufügen
import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Wunsch;

// Ein DTO (Data Transfer Object) um die eingehenden Daten zu empfangen
public class PlanungsanfrageDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate von;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate bis;

    private String ressort;

    private List<Mitarbeiter> mitarbeiterList; 

    private List<Wunsch> wuensche;

    // NEU: Feld für die Abwesenheiten hinzufügen
    private List<Abwesenheit> alleAbwesenheiten;

    // Konstruktor (optional, aber hilfreich)
    public PlanungsanfrageDto() {
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

    public List<Wunsch> getWuensche() {
        return wuensche;
    }

    public void setWuensche(List<Wunsch> wuensche) {
        this.wuensche = wuensche;
    }
    
    // NEU: Getter und Setter für die Abwesenheiten
    public List<Abwesenheit> getAlleAbwesenheiten() {
        return alleAbwesenheiten;
    }

    public void setAlleAbwesenheiten(List<Abwesenheit> alleAbwesenheiten) {
        this.alleAbwesenheiten = alleAbwesenheiten;
    }
    
    @Override
    public String toString() {
        return "PlanungsanfrageDto{" +
               "von=" + von +
               ", bis=" + bis +
               ", ressort='" + ressort + '\'' +
               ", mitarbeiterList=" + (mitarbeiterList != null ? mitarbeiterList.size() : 0) + " Mitarbeiter" +
               ", wuensche=" + (wuensche != null ? wuensche.size() : 0) + " Wünsche" +
               ", alleAbwesenheiten=" + (alleAbwesenheiten != null ? alleAbwesenheiten.size() : 0) + " Abwesenheiten" +
               '}';
    }
}