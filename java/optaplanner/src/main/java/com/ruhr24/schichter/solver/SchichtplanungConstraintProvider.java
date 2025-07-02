package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;
import org.optaplanner.core.api.score.stream.ConstraintCollectors;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    private static final long PENALTY_HARD = 1000L;
    private static final long PENALTY_MEDIUM = 50L;
    private static final long PENALTY_LOW = 10L;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
                // ==========================================================
                // HARD: Diese Regeln dürfen NIE gebrochen werden
                // ==========================================================
                mitarbeiterMussQualifiziertSein(constraintFactory),
                keineUeberlappendenSchichten(constraintFactory),
                mindestens11StundenRuhezeit(constraintFactory),
                
                // ==========================================================
                // SOFT: Diese Regeln definieren, was ein "guter" Plan ist
                // ==========================================================

                // HÖCHSTE PRIORITÄT (SOFT):
                //unbesetzteSchichtenBestrafen(constraintFactory), // Lieber eine Regel brechen, als eine Schicht unbesetzt zu lassen
                abweichungVonSollStundenMinimieren(constraintFactory), // DAS HAUPTZIEL: Stunden müssen stimmen!

                // MITTLERE PRIORITÄT (SOFT):
                bevorzugeEineSchichtartProWoche(constraintFactory), // Deine neue Regel für Block-Struktur
                //nichtMehrAlsFuenfTageAmStueckArbeiten(constraintFactory),
                wochenendeImmerZusammenHalten(constraintFactory),
                
                // NIEDRIGE PRIORITÄT (SOFT):
                cvdDiensteGleichmaessigVerteilen(constraintFactory)
        };
    }

    // =================================================================================
    // HARD CONSTRAINTS
    // =================================================================================

    /** Regel: Mitarbeiter muss die fachliche Qualifikation (z.B. CvD) für eine Schicht besitzen. */
    protected Constraint mitarbeiterMussQualifiziertSein(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && !erfuelltFachlicheQualifikation(s.getMitarbeiter(), s))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD), (s) -> 100)
                .asConstraint("Mitarbeiter fachlich nicht qualifiziert");
    }
    
    /** NEU: Regel: Mitarbeiter darf nur Schichten nehmen, die zu seinem Arbeitszeitmodell passen. */
    protected Constraint passendeSchichtartFuerMitarbeiter(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && !istSchichtFuerMitarbeiterZulaessig(s.getMitarbeiter(), s))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD), (s) -> 100)
                .asConstraint("Unpassende Schichtart für Mitarbeiter");
    }

    protected Constraint keineUeberlappendenSchichten(ConstraintFactory cf) {
        return cf.forEachUniquePair(Schicht.class,
                        Joiners.equal(Schicht::getMitarbeiter),
                        Joiners.overlapping(Schicht::getStartDateTime, Schicht::getEndDateTime))
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Überlappende Schichten");
    }

    protected Constraint mindestens11StundenRuhezeit(ConstraintFactory cf) {
        return cf.forEachUniquePair(Schicht.class, Joiners.equal(Schicht::getMitarbeiter))
                .filter((s1, s2) -> {
                    LocalDateTime end = s1.getEndDateTime();
                    LocalDateTime start = s2.getStartDateTime();
                    if (start.isBefore(end)) { end = s2.getEndDateTime(); start = s1.getStartDateTime(); }
                    return Duration.between(end, start).toHours() < 11;
                })
                .penalize(HardSoftLongScore.ofHard(PENALTY_HARD))
                .asConstraint("Weniger als 11 Stunden Ruhezeit");
    }

    // =================================================================================
    // SOFT CONSTRAINTS
    // =================================================================================


    /**
     * SOFT-REGEL: Mitarbeiter sollen möglichst die ganze Woche beim gleichen Schicht-Typ bleiben.
     * Das erzeugt die gewünschten "Blöcke", ohne starr zu sein.
     */
    private Constraint bevorzugeEineSchichtartProWoche(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null)
                // Gruppiert pro Mitarbeiter, pro Woche, die verschiedenen Schicht-Typen, die er arbeitet
                .groupBy(Schicht::getMitarbeiter,
                        s -> s.getDatum().get(WeekFields.ISO.weekOfWeekBasedYear()),
                        ConstraintCollectors.toSet(Schicht::getSchichtTyp))
                // Bestrafe es, wenn ein Mitarbeiter in einer Woche mehr als EINEN Schicht-Typ hat
                .filter((mitarbeiter, woche, schichtTypen) -> schichtTypen.size() > 1)
                .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM),
                        (mitarbeiter, woche, schichtTypen) -> schichtTypen.size() - 1)
                .asConstraint("Bevorzuge eine Schichtart pro Woche");
    }

    /** Wichtigste Soft-Regel: Möglichst nah an die vertraglichen Gesamtstunden kommen. */
    protected Constraint abweichungVonSollStundenMinimieren(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && s.getMitarbeiter().getWochenstunden() > 0)
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.sumLong(Schicht::getArbeitszeitInMinuten))
                // KORREKTUR: Die moderne Schreibweise ohne den String am Anfang verwenden
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_LOW),
                        (mitarbeiter, totalMinutes) -> {
                            long targetMinutes = (long) (mitarbeiter.getWochenstunden() * 60L * 4.0); // 4 Wochen Plan
                            long diff = Math.abs(totalMinutes - targetMinutes);
                            return diff * diff; // Quadratische Bestrafung macht große Abweichungen sehr teuer
                        })
                .asConstraint("Abweichung von Soll-Stunden"); // KORREKTUR: .asConstraint() am Ende
    }

    protected Constraint unbesetzteSchichtenBestrafen(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(schicht -> schicht.getMitarbeiter() == null)
                .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
                .asConstraint("Unbesetzte Schicht");
    }

    /** Wichtigste Soft-Regel: Möglichst nah an die vertraglichen Gesamtstunden kommen. */
    protected Constraint wochenstundenSollErreichtWerden(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && s.getMitarbeiter().getWochenstunden() > 0)
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.sumLong(Schicht::getArbeitszeitInMinuten))
                .penalizeLong("Abweichung von Soll-Stunden", HardSoftLongScore.ofSoft(PENALTY_LOW),
                        (mitarbeiter, totalMinutes) -> {
                            long targetMinutes = (long) (mitarbeiter.getWochenstunden() * 60L * 4.0); // 4 Wochen Plan
                            return Math.abs(totalMinutes - targetMinutes);
                        });
    }

    protected Constraint wochenendeImmerZusammenHalten(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && (s.getDatum().getDayOfWeek() == DayOfWeek.SATURDAY || s.getDatum().getDayOfWeek() == DayOfWeek.SUNDAY))
                .groupBy(Schicht::getMitarbeiter, s -> s.getDatum().get(WeekFields.ISO.weekOfWeekBasedYear()),
                         ConstraintCollectors.toSet(s -> s.getDatum().getDayOfWeek()))
                .filter((mitarbeiter, woche, tage) -> tage.size() == 1)
                .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
                .asConstraint("Wochenende als Paar");
    }

    protected Constraint nichtMehrAlsFuenfTageAmStueckArbeiten(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null)
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.toList())
                // KORREKTUR: Die moderne Schreibweise ohne den String am Anfang verwenden
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_LOW),
                        (mitarbeiter, schichtListe) -> {
                            schichtListe.sort(Comparator.comparing(Schicht::getDatum));
                            long penalty = 0, consecutiveDays = 1;
                            LocalDate previousDate = null;
                            for (Schicht schicht : schichtListe) {
                                LocalDate date = schicht.getDatum();
                                if (previousDate != null && !date.isEqual(previousDate)) {
                                    if (ChronoUnit.DAYS.between(previousDate, date) == 1) {
                                        consecutiveDays++;
                                    } else {
                                        consecutiveDays = 1;
                                    }
                                    if (consecutiveDays > 5) {
                                        penalty++;
                                    }
                                }
                                previousDate = date;
                            }
                            return penalty;
                        })
                .asConstraint("Mehr als 5 Tage am Stück arbeiten"); // KORREKTUR: .asConstraint() am Ende
    }
    
    protected Constraint cvdDiensteGleichmaessigVerteilen(ConstraintFactory cf) {
        return cf.forEach(Schicht.class)
                .filter(s -> s.getMitarbeiter() != null && s.getSchichtTyp().contains("CVD"))
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.count())
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_LOW), (mitarbeiter, count) -> count * count)
                .asConstraint("Ungleiche Verteilung der CvD-Dienste");
    }

    // =================================================================================
    // HILFSMETHODEN
    // =================================================================================
    
    private boolean erfuelltFachlicheQualifikation(Mitarbeiter mitarbeiter, Schicht schicht) {
        String schichtTyp = schicht.getSchichtTyp();
        if (schichtTyp != null && schichtTyp.contains("CVD")) {
            return mitarbeiter.hasQualification("CVD_QUALIFIKATION");
        }
        return true;
    }

    private boolean istSchichtFuerMitarbeiterZulaessig(Mitarbeiter mitarbeiter, Schicht schicht) {
        int wochenstunden = mitarbeiter.getWochenstunden();
        String schichtTyp = schicht.getSchichtTyp();

        if (wochenstunden >= 32) {
            // 40h-Kräfte sollen nur 8h-Dienste oder CvD-Dienste machen
            return schichtTyp.equals("8-Stunden-Dienst") || schichtTyp.contains("CVD");
        }
        if (wochenstunden == 30) {
            // 30-39h-Kräfte können 8h oder 6h
            return schichtTyp.equals("6-Stunden-Dienst");
        }
        if (wochenstunden == 24) {
            // 20-29h-Kräfte können 8h, 6h, 4h
            return schichtTyp.equals("8-Stunden-Dienst");
        }
        if (wochenstunden == 20) {
            // Alle darunter können alles unter 8h machen
            return schichtTyp.equals("8-Stunden-Dienst") || schichtTyp.equals("4-Stunden-Dienst");
        }
        if (wochenstunden < 20) {
            // Alle darunter können alles unter 8h machen
            return schichtTyp.equals("8-Stunden-Dienst");
        }
        return true; // Fallback
    }
}