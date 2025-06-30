package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.lookup.PlanningId;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;
import java.time.Duration; // Import für Duration
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors; // Stellen Sie sicher, dass dies importiert ist

public class Mitarbeiter {

    @PlanningId // OptaPlanner benötigt eine eindeutige ID
    private String id; // Eindeutige interne ID (z.B. 3-stellig)
    private String nachname;
    private String vorname;
    private String email; // NEU: E-Mail-Adresse
    private String stellenbezeichnung; // NEU: Z.B. "Online Redakteurin Buzz"
    private String ressort; // Beibehalten als String
    private int wochenstunden; // Geplante Wochenarbeitszeit (int)
    private boolean cvd; // Ist der Mitarbeiter ein Chef vom Dienst?
    private String notizen; // Notizen zum Mitarbeiter
    private int targetBiWeeklyHours; // Ziel-Stunden für den 2-Wochen-Zeitraum

    // NEU: Liste für detailliertere Rollen/Qualifikationen aus der 'Stelle'-Spalte
    private List<String> rollenUndQualifikationen;
    // NEU: Liste für Team-Zugehörigkeiten und weitere Infos aus der 'Teams'-Spalte
    private List<String> teamsUndZugehoerigkeiten;

    // Bestehende NEUE FELDER für Soft Constraints
    private List<Schicht> wunschschichten; // Liste der Schichten, die der Mitarbeiter gerne hätte
    // private Set<LocalDate> urlaubstageSet; // Set von Daten, an denen der Mitarbeiter Urlaub hat - AUSKOMMENTIERT

    // NEU: Liste der SchichtBlöcke, die diesem Mitarbeiter zugewiesen wurden (wird von OptaPlanner gesetzt)
    private List<SchichtBlock> assignedSchichtBlocks;


    // Standardkonstruktor (für Spring/JSON-Deserialisierung)
    public Mitarbeiter() {
        // Sicherstellen, dass Listen und Sets immer initialisiert sind, um NullPointerExceptions zu vermeiden
        this.wunschschichten = new ArrayList<>();
        // this.urlaubtageSet = new HashSet<>(); // AUSKOMMENTIERT
        this.rollenUndQualifikationen = new ArrayList<>();
        this.teamsUndZugehoerigkeiten = new ArrayList<>();
        this.assignedSchichtBlocks = new ArrayList<>(); // Initialisieren
    }

    // Vollständiger Konstruktor (erweitert um neue Felder und angepasste Typen)
    public Mitarbeiter(String id, String nachname, String vorname, String email, String stellenbezeichnung,
                       String ressort, int wochenstunden, boolean cvd, String notizen,
                       List<String> rollenUndQualifikationen, List<String> teamsUndZugehoerigkeiten,
                       List<Schicht> wunschschichten,
                       int targetBiWeeklyHours/*, Set<LocalDate> urlaubstageSet*/) { // AUSKOMMENTIERT
        this.id = id;
        this.nachname = nachname;
        this.vorname = vorname;
        this.email = email;
        this.stellenbezeichnung = stellenbezeichnung;
        this.ressort = ressort;
        this.wochenstunden = wochenstunden;
        this.cvd = cvd;
        this.notizen = notizen;
        // Defensive Kopien, um externe Änderungen an den Listen zu verhindern
        this.rollenUndQualifikationen = rollenUndQualifikationen != null ? new ArrayList<>(rollenUndQualifikationen) : new ArrayList<>();
        this.teamsUndZugehoerigkeiten = teamsUndZugehoerigkeiten != null ? new ArrayList<>(teamsUndZugehoerigkeiten) : new ArrayList<>();
        this.wunschschichten = wunschschichten != null ? new ArrayList<>(wunschschichten) : new ArrayList<>();
        // this.urlaubtageSet = urlaubtageSet != null ? new HashSet<>(urlaubtageSet) : new HashSet<>(); // AUSKOMMENTIERT
        this.targetBiWeeklyHours = targetBiWeeklyHours;
        this.assignedSchichtBlocks = new ArrayList<>(); // Initialisieren
    }

    // Vereinfachter Konstruktor für den MitarbeiterLoader (falls du ihn noch brauchst)
    // Passt sich an die CSV-Struktur an, die du zuletzt gezeigt hast
    public Mitarbeiter(String id, String nachname, String vorname, String email, String stellenbezeichnung,
                       String ressort, boolean isCvd, List<String> rollenUndQualifikationen,
                       List<String> teamsUndZugehoerigkeiten, String notizen, int wochenstunden, int targetBiWeeklyHours) {
        this(id, nachname, vorname, email, stellenbezeichnung, ressort, wochenstunden, isCvd, notizen,
                rollenUndQualifikationen, teamsUndZugehoerigkeiten, new ArrayList<>(), targetBiWeeklyHours/*, new HashSet<>()*/); // AUSKOMMENTIERT
    }


    // --- Getter und Setter ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNutzerID() { // Beibehalten für Kompatibilität mit dem Controller/CSV
        return this.id;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(String nachname) {
        this.nachname = nachname;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStellenbezeichnung() {
        return stellenbezeichnung;
    }

    public void setStellenbezeichnung(String stellenbezeichnung) {
        this.stellenbezeichnung = stellenbezeichnung;
    }

    public List<String> getRollenUndQualifikationen() {
        return rollenUndQualifikationen;
    }

    public void setRollenUndQualifikationen(List<String> rollenUndQualifikationen) {
        // Sicherstellen, dass beim Setzen eine defensive Kopie verwendet wird
        this.rollenUndQualifikationen = rollenUndQualifikationen != null ? new ArrayList<>(rollenUndQualifikationen) : new ArrayList<>();
    }

    public List<String> getTeamsUndZugehoerigkeiten() {
        return teamsUndZugehoerigkeiten;
    }

    public void setTeamsUndZugehoerigkeiten(List<String> teamsUndZugehoerigkeiten) {
        // Sicherstellen, dass beim Setzen eine defensive Kopie verwendet wird
        this.teamsUndZugehoerigkeiten = teamsUndZugehoerigkeiten != null ? new ArrayList<>(teamsUndZugehoerigkeiten) : new ArrayList<>();
    }

    public int getTargetBiWeeklyHours() {
        return targetBiWeeklyHours;
    }

    public void setTargetBiWeeklyHours(int targetBiWeeklyHours) {
        this.targetBiWeeklyHours = targetBiWeeklyHours;
    }
    
    public String getRessort() {
        return ressort;
    }

    public void setRessort(String ressort) {
        this.ressort = ressort;
    }

    public int getWochenstunden() { // Typ int
        return wochenstunden;
    }

    public void setWochenstunden(int wochenstunden) { // Typ int
        this.wochenstunden = wochenstunden;
    }

    public boolean isCVD() { // Methode ist isCVD()
        return cvd;
    }

    public void setCVD(boolean cvd) { // Methode ist setCVD()
        this.cvd = cvd;
    }

    public String getNotizen() {
        return notizen;
    }

    public void setNotizen(String notizen) {
        this.notizen = notizen;
    }

    public List<Schicht> getWunschschichten() {
        return wunschschichten;
    }

    public void setWunschschichten(List<Schicht> wunschschichten) {
        this.wunschschichten = wunschschichten != null ? new ArrayList<>(wunschschichten) : new ArrayList<>();
    }

    public Set<LocalDate> getUrlaubstageSet() {
        // return urlaubtageSet; // AUSKOMMENTIERT
        return new HashSet<>(); // Temporäre leere Menge zurückgeben, wenn das Feature nicht aktiv ist
    }

    public void setUrlaubtageSet(Set<LocalDate> urlaubstageSet) {
        // this.urlaubtageSet = urlaubtageSet != null ? new HashSet<>(urlaubstageSet) : new HashSet<>(); // AUSKOMMENTIERT
        // Nichts tun, da das Feature nicht aktiv ist
    }

    @JsonIgnore
    public List<SchichtBlock> getAssignedSchichtBlocks() {
        return assignedSchichtBlocks;
    }

    public void setAssignedSchichtBlocks(List<SchichtBlock> assignedSchichtBlocks) {
        this.assignedSchichtBlocks = assignedSchichtBlocks;
    }

    // NEU: Hilfsmethode, um zu prüfen, ob der Mitarbeiter eine bestimmte Qualifikation hat
    // DIESE METHODE FEHLTE UND WURDE HINZUGEFÜGT
    public boolean hasQualification(String qualification) {
        return rollenUndQualifikationen.contains(qualification);
    }

    // NEU: Hilfsmethode, um zu prüfen, ob der Mitarbeiter ALLE erforderlichen Qualifikationen hat
    // DIESE METHODE FEHLTE UND WURDE HINZUGEFÜGT
    public boolean hasAllQualifikationen(List<String> requiredQualifikations) {
        if (requiredQualifikations == null || requiredQualifikations.isEmpty()) {
            return true; // Keine Qualifikationen erforderlich, also ist der Mitarbeiter qualifiziert
        }
        // Wichtig: Set<String> requiredQualifikations sollte hier verwendet werden,
        // da SchichtBlock::getRequiredQualifikationen ein Set zurückgibt.
        // Wenn SchichtBlock::getRequiredQualifikationen eine List zurückgibt, ist dies korrekt.
        // Gehe davon aus, dass SchichtBlock getRequiredQualifikationen ein List<String> zurückgibt
        return rollenUndQualifikationen.containsAll(requiredQualifikations);
    }

    // NEUE HILFSMETHODE: Summiert die Gesamtdauer aller zugewiesenen SchichtBlöcke in Minuten
    // Diese Methode ist für die employeeCannotExceedWeeklyHoursHard Constraint ESSENTIELL.
    public long getTotalAssignedDurationInMinutes() {
        // Stellt sicher, dass assignedSchichtBlocks nicht null ist, bevor gestreamt wird.
        if (assignedSchichtBlocks == null) {
            return 0;
        }
        return assignedSchichtBlocks.stream()
                .mapToLong(SchichtBlock::getTotalDurationInMinutes)
                .sum();
    }


    // Optional: Eine Methode, um den vollständigen Namen zu bekommen
    public String getVollerName() {
        return vorname + " " + nachname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mitarbeiter that = (Mitarbeiter) o;
        // Wichtig: equals und hashCode basieren auf der @PlanningId
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Mitarbeiter{" +
               "id='" + id + '\'' +
               ", nachname='" + nachname + '\'' +
               ", vorname='" + vorname + '\'' +
               ", email='" + email + '\'' +
               ", stellenbezeichnung='" + stellenbezeichnung + '\'' +
               ", ressort='" + ressort + '\'' +
               ", wochenstunden=" + wochenstunden +
               ", cvd=" + cvd +
               ", notizen='" + notizen + '\'' +
               ", rollenUndQualifikationen=" + (rollenUndQualifikationen != null ? String.join(", ", rollenUndQualifikationen) : "[]") +
               ", teamsUndZugehoerigkeiten=" + (teamsUndZugehoerigkeiten != null ? String.join(", ", teamsUndZugehoerigkeiten) : "[]") +
               ", wunschschichten=" + (wunschschichten != null ? wunschschichten.size() + " Schichten" : "0 Schichten") +
               ", assignedSchichtBlocks=" + (assignedSchichtBlocks != null ? assignedSchichtBlocks.size() + " Blöcke" : "0 Blöcke") +
               '}';
    }
}
