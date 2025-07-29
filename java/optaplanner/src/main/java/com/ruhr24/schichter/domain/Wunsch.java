package com.ruhr24.schichter.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.time.LocalTime;

/**
 * Repräsentiert einen einzelnen Wunsch eines Mitarbeiters.
 */

public class Wunsch {

    private String mitarbeiterId;

    private Date datum;

    private WunschTyp typ;


    @JsonFormat(pattern = "HH:mm")
    private LocalTime von;
    
    @JsonFormat(pattern = "HH:mm")
    private LocalTime bis;


    public Wunsch() {
    }

    public String getMitarbeiterId() {
        return mitarbeiterId;
    }
        
    
    public void setMitarbeiterId(String mitarbeiterId) {
        this.mitarbeiterId = mitarbeiterId;
    }

    public Date getDatum() {
        return datum;
    }
    
    public void setDatum(Date datum) {
       this.datum = datum;
    }   
        
    public WunschTyp getTyp() {
        return typ;
    }
    
    public void setTyp(WunschTyp typ) {
        this.typ = typ;
    }
    
    public LocalTime getVon() {
        return von;
    }
    
    public void setVon(LocalTime von) {
        this.von = von;
    }
    
    public LocalTime getBis() {
        return bis;
    }
    
    public void setBis(LocalTime bis) {
        this.bis = bis;
    }
}