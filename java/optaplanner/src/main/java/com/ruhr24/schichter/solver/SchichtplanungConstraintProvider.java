package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
//import com.ruhr24.schichter.domain.SchichtPlan;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;
import org.optaplanner.core.api.score.stream.ConstraintCollectors;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate; // <-- FEHLENDER IMPORT HINZUGEFÜGT
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
//import java.util.SortedSet;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    private static final long PENALTY_HARD = 100L;
    private static final long PENALTY_MEDIUM = 50L;
    private static final long PENALTY_LOW = 10L;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
                // HARD CONSTRAINTS
                mitarbeiterMussQualifiziertSein(constraintFactory),
                keineUeberlappendenSchichten(constraintFactory),
                mindestens11StundenRuhezeit(constraintFactory),
                wochenstundenDuerfenNichtUeberschrittenWerden(constraintFactory),
                inkompatibleCvDsNichtAmSelbenTag(constraintFactory),
                WochenStundenMuessenPassen(constraintFactory),

                // SOFT CONSTRAINTS
                unbesetzteSchichtbloeckeBestrafen(constraintFactory),
                abweichungVonSollStundenMinimieren(constraintFactory),
                wochenendenGleichmaessigVerteilen(constraintFactory),
                nichtMehrAlsFuenfTageAmStueckArbeiten(constraintFactory),
                cvdDiensteGleichmaessigVerteilen(constraintFactory)
        };
    }

    // =================================================================================
    // HARD CONSTRAINTS IMPLEMENTIERUNGEN
    // =================================================================================

    protected Constraint WochenStundenMuessenPassen(ConstraintFactory constraintFactory) {
        // Definiere hier, welcher Block-Typ welche Stundenzahl erfordert.
        Map<String, Integer> exklusiveBlockStunden = Map.of(
            "KERN_MO_DO_32H_BLOCK", 32,
            "KERN_30H_BLOCK", 30,
            "KERN_24H_BLOCK", 24,
            "KERN_MO_FR_20H_BLOCK", 20,
            "KERN_19H_BLOCK", 19,
            "KERN_15H_BLOCK", 15
            // Füge hier weitere exklusive Blöcke hinzu, falls nötig.
        );

        return constraintFactory.forEach(SchichtBlock.class)
                // Nimm nur zugewiesene Blöcke, die ein exklusiver Typ sind.
                .filter(block -> block.getMitarbeiter() != null &&
                                 exklusiveBlockStunden.containsKey(block.getBlockTyp()))
                // Bestrafe, wenn die Stundenzahl des Mitarbeiters nicht passt.
                .filter(block -> block.getMitarbeiter().getWochenstunden() != exklusiveBlockStunden.get(block.getBlockTyp()))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Exklusiver Block nur für passende Stundenanzahl");
    }

    protected Constraint mitarbeiterMussQualifiziertSein(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(block -> block.getMitarbeiter() != null && !block.getMitarbeiter().hasAllQualifikationen(block.getRequiredQualifikationen()))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Mitarbeiter nicht qualifiziert");
    }

    protected Constraint keineUeberlappendenSchichten(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Schicht.class)
                .filter(schicht -> schicht.getMitarbeiter() != null)
                .join(Schicht.class,
                        Joiners.equal(Schicht::getMitarbeiter),
                        Joiners.lessThan(Schicht::getId),
                        Joiners.overlapping(Schicht::getStartDateTime, Schicht::getEndDateTime))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Überlappende Schichten");
    }

    protected Constraint mindestens11StundenRuhezeit(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Schicht.class)
                .filter(schicht -> schicht.getMitarbeiter() != null)
                .join(Schicht.class,
                        Joiners.equal(Schicht::getMitarbeiter),
                        Joiners.lessThan(Schicht::getId))
                .filter((schicht1, schicht2) -> {
                    Duration duration = Duration.between(schicht1.getEndDateTime(), schicht2.getStartDateTime());
                    if (schicht2.getEndDateTime().isBefore(schicht1.getStartDateTime())) {
                         duration = Duration.between(schicht2.getEndDateTime(), schicht1.getStartDateTime());
                    }
                    return !duration.isNegative() && duration.toHours() < 11;
                })
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Weniger als 11 Stunden Ruhezeit");
    }

    /** Regel: Die Summe der Arbeitsstunden eines Mitarbeiters darf sein Soll für den GESAMTEN ZEITRAUM nicht überschreiten. KORRIGIERT. */
    protected Constraint wochenstundenDuerfenNichtUeberschrittenWerden(ConstraintFactory constraintFactory) {
        // ACHTUNG: Passe diese Zahl an, wenn dein Planungszeitraum anders ist (z.B. 2.0 für zwei Wochen).
        final double PLANNING_WEEKS = 4.0;

        return constraintFactory.forEach(SchichtBlock.class)
                .filter(block -> block.getMitarbeiter() != null && block.getMitarbeiter().getWochenstunden() > 0)
                .groupBy(SchichtBlock::getMitarbeiter,
                        ConstraintCollectors.sumLong(SchichtBlock::getTotalDurationInMinutes))
                .filter((mitarbeiter, totalMinutes) -> {
                    long targetMinutes = (long) (mitarbeiter.getWochenstunden() * 60L * PLANNING_WEEKS);
                    return totalMinutes > targetMinutes;
                })
                .penalizeLong(HardSoftLongScore.ofHard(1L),
                        (mitarbeiter, totalMinutes) -> {
                            long targetMinutes = (long) (mitarbeiter.getWochenstunden() * 60L * PLANNING_WEEKS);
                            return totalMinutes - targetMinutes;
                        })
                .asConstraint("Wochenstunden überschritten");
    }

    protected Constraint inkompatibleCvDsNichtAmSelbenTag(ConstraintFactory constraintFactory) {
        Map<String, List<String>> incompatiblePairs = Map.of(
                "Ina", List.of("Nicolas", "Kevin"),
                "Kevin", List.of("Ina", "Nicolas"),
                "Nicolas", List.of("Ina", "Kevin")
        );
        return constraintFactory.forEachUniquePair(Schicht.class, Joiners.equal(Schicht::getDatum))
                .filter((schicht1, schicht2) -> {
                    Mitarbeiter m1 = schicht1.getMitarbeiter();
                    Mitarbeiter m2 = schicht2.getMitarbeiter();
                    if (m1 == null || m2 == null) return false;
                    List<String> incompatibles = incompatiblePairs.get(m1.getVorname());
                    return incompatibles != null && incompatibles.contains(m2.getVorname());
                })
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Inkompatible Mitarbeiter am selben Tag");
    }

    // =================================================================================
    // SOFT CONSTRAINTS IMPLEMENTIERUNGEN
    // =================================================================================

    protected Constraint unbesetzteSchichtbloeckeBestrafen(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(block -> block.getMitarbeiter() == null)
                .penalize(HardSoftLongScore.ofSoft(PENALTY_HARD))
                .asConstraint("Unbesetzter Schichtblock");
    }
    
    /** Regel: Die Abweichung von der Soll-Arbeitszeit soll minimiert werden. KORRIGIERT & VEREINFACHT. */
    protected Constraint abweichungVonSollStundenMinimieren(ConstraintFactory constraintFactory) {
        // ACHTUNG: Passe diese Zahl an, wenn dein Planungszeitraum anders ist.
        final double PLANNING_WEEKS = 4.0;

        return constraintFactory.forEach(SchichtBlock.class)
                .filter(block -> block.getMitarbeiter() != null && block.getMitarbeiter().getWochenstunden() > 0)
                .groupBy(SchichtBlock::getMitarbeiter,
                        ConstraintCollectors.sumLong(SchichtBlock::getTotalDurationInMinutes))
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_LOW),
                        (mitarbeiter, totalMinutes) -> {
                            long targetMinutes = (long) (mitarbeiter.getWochenstunden() * 60L * PLANNING_WEEKS);
                            return Math.abs(totalMinutes - targetMinutes);
                        })
                .asConstraint("Abweichung von Soll-Stunden");
    }

    protected Constraint wochenendenGleichmaessigVerteilen(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Schicht.class)
                .filter(schicht -> schicht.getMitarbeiter() != null &&
                                  (schicht.getDatum().getDayOfWeek() == DayOfWeek.SATURDAY || schicht.getDatum().getDayOfWeek() == DayOfWeek.SUNDAY))
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.countDistinct(Schicht::getDatum))
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_MEDIUM),
                        (mitarbeiter, weekendDays) -> weekendDays * weekendDays)
                .asConstraint("Ungleiche Verteilung der Wochenenddienste");
    }
    
    /** KORRIGIERTE LOGIK: Sammelt jetzt die Daten korrekt als `SortedSet<LocalDate>`. */
    /** KORRIGIERTE LOGIK: Sammelt jetzt die Schichten und iteriert dann darüber. */
    protected Constraint nichtMehrAlsFuenfTageAmStueckArbeiten(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Schicht.class)
                .filter(schicht -> schicht.getMitarbeiter() != null)
                // Wir sammeln die ganzen Schicht-Objekte, sortiert nach Datum
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.toSortedSet(Comparator.comparing(Schicht::getDatum)))
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_MEDIUM),
                        (mitarbeiter, sortedShifts) -> {
                            long penalty = 0;
                            if (sortedShifts.isEmpty()) {
                                return 0L;
                            }

                            long consecutiveDays = 0;
                            LocalDate previousDate = null;
                            // Die Schleife iteriert jetzt über Schicht-Objekte, nicht direkt über Daten
                            for (Schicht schicht : sortedShifts) {
                                LocalDate date = schicht.getDatum(); // Wir holen das Datum aus der Schicht
                                if (previousDate != null) {
                                    if (date.isEqual(previousDate)) {
                                        continue; // Selber Tag, keine neue Zählung
                                    }
                                    if (ChronoUnit.DAYS.between(previousDate, date) == 1) {
                                        consecutiveDays++;
                                    } else {
                                        consecutiveDays = 1; // Lücke gefunden, Zähler zurücksetzen
                                    }
                                } else {
                                    consecutiveDays = 1; // Erster Tag in der Zählung
                                }

                                if (consecutiveDays > 5) {
                                    penalty++;
                                }
                                previousDate = date;
                            }
                            return penalty;
                        })
                .asConstraint("Mehr als 5 Tage am Stück arbeiten");
    }

    protected Constraint cvdDiensteGleichmaessigVerteilen(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(block -> block.getMitarbeiter() != null && block.getRequiredQualifikationen().contains("CVD_QUALIFIKATION"))
                .groupBy(SchichtBlock::getMitarbeiter, ConstraintCollectors.count())
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_MEDIUM),
                        (mitarbeiter, count) -> count * count)
                .asConstraint("Ungleiche Verteilung der CvD-Dienste");
    }
}