package com.ruhr24.schichter.domain; // Passe das Paket an, falls nötig!

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@PlanningEntity // Markiert diese Klasse als planbare Entität
public class SchichtBlock {

    @PlanningId
    private Long id; // Eindeutige ID für den SchichtBlock

    private String name; // Name des SchichtBlocks (z.B. "CvD Frühdienst 5 Tage", "Standard Woche")

    // Die @PlanningVariable Annotation ist jetzt auf dem Getter!
    private Mitarbeiter mitarbeiter; // Der Mitarbeiter, der diesem Block ZUGETEILT wird (Planning Variable)

    // Liste der einzelnen Schichten, die diesen Block ausmachen
    // WICHTIG: Diese Schichten sind hier VORDEFINIERT und werden nicht vom Planner einzeln verschoben.
    // Sie sind ein Bestandteil des Blocks.
    private List<Schicht> schichtenImBlock;

    // Optional: Start- und Enddatum des Blocks, um die Position im Kalender zu fixieren/definieren
    private LocalDate blockStartDatum;
    private LocalDate blockEndDatum;

    // Optional: Art des Blocks (z.B. "CVD_RANDDIENST", "STANDARD_REDAKTION", "WERKSTUDENT_MODUL")
    private String blockTyp;

    // NEUES FELD: Liste der benötigten Qualifikationen für diesen SchichtBlock
    private List<String> requiredQualifikationen;

    // OptaPlanner benötigt einen No-Arg-Konstruktor
    public SchichtBlock() {
        this.schichtenImBlock = new ArrayList<>();
        this.requiredQualifikationen = new ArrayList<>(); // Sicherstellen, dass die Liste initialisiert ist
    }

    // ORIGINAL-KONSTRUKTOR mit 6 Parametern (bleibt für Kompatibilität bestehen, wenn noch alte Aufrufe existieren)
    public SchichtBlock(Long id, String name, List<Schicht> schichtenImBlock, LocalDate blockStartDatum, LocalDate blockEndDatum, String blockTyp) {
        this(id, name, schichtenImBlock, blockStartDatum, blockEndDatum, blockTyp, new ArrayList<>()); // Ruft den neuen Konstruktor auf
    }

    // NEUER, ERWEITERTER KONSTRUKTOR mit 7 Parametern, der die Qualifikationen annimmt
    // Dies ist der Konstruktor, den der SchichtBlockGenerator aufrufen will.
    public SchichtBlock(Long id, String name, List<Schicht> schichtenImBlock,
                        LocalDate blockStartDatum, LocalDate blockEndDatum,
                        String blockTyp, List<String> requiredQualifikationen) {
        this.id = id;
        this.name = name;
        this.schichtenImBlock = schichtenImBlock != null ? new ArrayList<>(schichtenImBlock) : new ArrayList<>();
        this.blockStartDatum = blockStartDatum;
        this.blockEndDatum = blockEndDatum;
        this.blockTyp = blockTyp;
        this.requiredQualifikationen = requiredQualifikationen != null ? new ArrayList<>(requiredQualifikationen) : new ArrayList<>();
    }

    // --- Getter und Setter ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @PlanningVariable(valueRangeProviderRefs = {"mitarbeiterRange"}) // OptaPlanner soll hier Mitarbeiter zuweisen
    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
    }

    public List<Schicht> getSchichtenImBlock() {
        return schichtenImBlock;
    }

    public void setSchichtenImBlock(List<Schicht> schichtenImBlock) {
        this.schichtenImBlock = schichtenImBlock;
    }

    public LocalDate getBlockStartDatum() {
        return blockStartDatum;
    }

    public void setBlockStartDatum(LocalDate blockStartDatum) {
        this.blockStartDatum = blockStartDatum;
    }

    public LocalDate getBlockEndDatum() {
        return blockEndDatum;
    }

    public void setBlockEndDatum(LocalDate blockEndDatum) {
        this.blockEndDatum = blockEndDatum;
    }

    public String getBlockTyp() {
        return blockTyp;
    }

    public void setBlockTyp(String blockTyp) {
        this.blockTyp = blockTyp;
    }

    public List<String> getRequiredQualifikationen() {
        if (requiredQualifikationen == null) {
            return new ArrayList<>();
        }
        return requiredQualifikationen;
    }

    public void setRequiredQualifikationen(List<String> requiredQualifikationen) {
        this.requiredQualifikationen = requiredQualifikationen;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchichtBlock that = (SchichtBlock) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SchichtBlock{" +
               "name='" + name + '\'' +
               ", id=" + id +
               ", mitarbeiter=" + (mitarbeiter != null ? mitarbeiter.getNachname() : "unassigned") +
               ", blockStartDatum=" + blockStartDatum +
               ", blockEndDatum=" + blockEndDatum +
               ", blockTyp='" + blockTyp + '\'' +
               ", requiredQualifikationen=" + requiredQualifikationen +
               ", schichtenAnzahl=" + (schichtenImBlock != null ? schichtenImBlock.size() : 0) +
               '}';
    }
}