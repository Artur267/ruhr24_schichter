// src/main/java/com.ruhr24.schichter.domain/SchichtBlock.java
package com.ruhr24.schichter.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID; // Wichtig: Import für UUID

@PlanningEntity // Markiert diese Klasse als planbare Entität
public class SchichtBlock {

    @PlanningId
    private UUID id; // Eindeutige ID für den SchichtBlock (jetzt UUID)

    private String name; // Name des SchichtBlocks (z.B. "CvD Frühdienst 5 Tage", "Standard Woche")

    // Der Mitarbeiter, der diesem Block ZUGETEILT wird (Planning Variable)
    @PlanningVariable(valueRangeProviderRefs = {"mitarbeiterRange"}) // OptaPlanner soll hier Mitarbeiter zuweisen
    private Mitarbeiter mitarbeiter;

    // Liste der einzelnen Schichten, die diesen Block ausmachen
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

    // ORIGINAL-KONSTRUKTOR mit 6 Parametern (bleibt für Kompatibilität bestehen)
    // Ruft den neuen, erweiterten Konstruktor auf.
    // ACHTUNG: Die ID ist jetzt UUID, nicht Long.
    public SchichtBlock(UUID id, String name, List<Schicht> schichtenImBlock, LocalDate blockStartDatum, LocalDate blockEndDatum, String blockTyp) {
        // WICHTIG: Hier new ArrayList<>() übergeben, um requiredQualifikationen zu initialisieren
        this(id, name, schichtenImBlock, blockStartDatum, blockEndDatum, blockTyp, new ArrayList<>());
    }

    // NEUER, ERWEITERTER KONSTRUKTOR mit 7 Parametern, der die Qualifikationen annimmt
    // Dies ist der Konstruktor, den der SchichtBlockGenerator aufrufen wird.
    // ACHTUNG: Die ID ist jetzt UUID, nicht Long.
    public SchichtBlock(UUID id, String name, List<Schicht> schichtenImBlock,
                        LocalDate blockStartDatum, LocalDate blockEndDatum,
                        String blockTyp, List<String> requiredQualifikationen) {
        this.id = id;
        this.name = name;
        // Defensive Kopie der Schichtenliste und Zuweisung des SchichtBlocks zu jeder Schicht
        this.schichtenImBlock = schichtenImBlock != null ? new ArrayList<>(schichtenImBlock) : new ArrayList<>();
        if (this.schichtenImBlock != null) {
            for (Schicht schicht : this.schichtenImBlock) {
                // WICHTIG: Setze die Referenz zum übergeordneten Block in jeder einzelnen Schicht
                schicht.setSchichtBlock(this);
            }
        }
        this.blockStartDatum = blockStartDatum;
        this.blockEndDatum = blockEndDatum;
        this.blockTyp = blockTyp;
        // Defensive Kopie der Qualifikationsliste
        this.requiredQualifikationen = requiredQualifikationen != null ? new ArrayList<>(requiredQualifikationen) : new ArrayList<>();
        // Mitarbeiter wird von OptaPlanner gesetzt, initial ist es null
        this.mitarbeiter = null;
    }

    // --- Getter und Setter ---
    // ACHTUNG: Getter/Setter für ID sind jetzt vom Typ UUID
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Getter für die Planning Variable
    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    // Setter für die Planning Variable
    // Wenn ein Mitarbeiter diesem Block zugewiesen wird,
    // weisen wir ihn auch jeder einzelnen Schicht im Block zu UND fügen den Block dem Mitarbeiter hinzu.
    public void setMitarbeiter(Mitarbeiter mitarbeiter) {
        this.mitarbeiter = mitarbeiter;
        if (this.schichtenImBlock != null) {
            for (Schicht schicht : this.schichtenImBlock) {
                schicht.setMitarbeiter(mitarbeiter);
            }
        }
        // WICHTIG: Füge diesen SchichtBlock zur Liste der zugewiesenen Blöcke des Mitarbeiters hinzu
        // Diese Logik kann auch in einem Listener nach dem Solver-Lauf erfolgen, um die Performance nicht zu beeinträchtigen.
        // Für jetzt behalten wir es, wenn du es so haben möchtest.
        if (mitarbeiter != null && !mitarbeiter.getAssignedSchichtBlocks().contains(this)) {
            mitarbeiter.getAssignedSchichtBlocks().add(this);
        }
    }

    public List<Schicht> getSchichtenImBlock() {
        // Rückgabe einer defensiven Kopie, um externe Änderungen an der Liste zu verhindern
        return new ArrayList<>(schichtenImBlock);
    }

    public void setSchichtenImBlock(List<Schicht> schichtenImBlock) {
        // Sicherstellen, dass beim Setzen eine defensive Kopie verwendet wird
        this.schichtenImBlock = schichtenImBlock != null ? new ArrayList<>(schichtenImBlock) : new ArrayList<>();
        // WICHTIG: Auch hier die Referenz zum übergeordneten Block setzen
        if (this.schichtenImBlock != null) {
            for (Schicht schicht : this.schichtenImBlock) {
                schicht.setSchichtBlock(this);
            }
        }
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
        // Rückgabe einer defensiven Kopie oder einer leeren Liste, nie null
        return requiredQualifikationen != null ? new ArrayList<>(requiredQualifikationen) : new ArrayList<>();
    }

    public void setRequiredQualifikationen(List<String> requiredQualifikationen) {
        // Sicherstellen, dass beim Setzen eine defensive Kopie verwendet wird
        this.requiredQualifikationen = requiredQualifikationen != null ? new ArrayList<>(requiredQualifikationen) : new ArrayList<>();
    }

    /**
     * Berechnet die gesamte Arbeitszeit (in Minuten) aller Schichten in diesem Block.
     * @return Gesamtdauer in Minuten.
     */
    public long getTotalDurationInMinutes() {
        if (schichtenImBlock == null) {
            return 0;
        }
        return schichtenImBlock.stream()
                .mapToLong(Schicht::getArbeitszeitInMinuten)
                .sum();
    }

    /**
     * Gibt das Startdatum des Blocks zurück (Alias für blockStartDatum).
     * @return Startdatum.
     */
    public LocalDate getStartDate() {
        return blockStartDatum;
    }

    /**
     * Gibt das Enddatum des Blocks zurück (Alias für blockEndDatum).
     * @return Enddatum.
     */
    public LocalDate getEndDate() {
        return blockEndDatum;
    }

    /**
     * Berechnet die Anzahl der Tage, die dieser Block umfasst.
     * @return Anzahl der Tage.
     */
    public long getTotalDays() {
        if (blockStartDatum == null || blockEndDatum == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(blockStartDatum, blockEndDatum) + 1;
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
