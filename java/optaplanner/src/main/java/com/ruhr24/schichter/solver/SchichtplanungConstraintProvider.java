package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.SchichtTyp;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;
import org.optaplanner.core.api.score.stream.ConstraintCollectors;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set; // Beibehalten, falls Set<String> für Qualifikationen verwendet wird
import java.util.stream.Collectors;
import java.util.function.Function;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    // HARD Constraint Weights (violations make the solution invalid)
    private static final long HARD_SCORE_PENALTY = 1L;


    // SOFT Constraint Weights (violations make the solution worse, but not invalid)
    // Weights increased to enforce fairer distribution.
    private static final long SOFT_PENALTY_TARGET_MINUTES_DEVIATION_PER_MINUTE = 2000L;
    private static final long SOFT_PENALTY_PREFERENCE_PART_TIME_BLOCK = 2000L;
    private static final long SOFT_PENALTY_NOT_PLANNED_PER_EMPLOYEE = 50000L; // Hohe Strafe für ungeplante Blöcke
    private static final long SOFT_PENALTY_MORE_THAN_5_CONSECUTIVE_DAYS = 50L;
    private static final long SOFT_PENALTY_CVD_DOUBLE_OCCUPANCY_DAY = 100L;
    private static final long SOFT_PENALTY_CVD_EVEN_DISTRIBUTION = 100L;
    private static final long SOFT_PENALTY_WEEKEND_SHIFTS = 50L;
    private static final long SOFT_PENALTY_CORE_SHIFT_EVEN_DISTRIBUTION = 50L;


    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            // --- HARD CONSTRAINTS (Deine aktive Auswahl) ---
            cvd7DayBlockIsExclusive(constraintFactory),
            //noOverlappingShifts(constraintFactory), // Auskommentiert, da "macht delta" - muss später individuell debugged werden
            employeeMustBeQualified(constraintFactory), // AKTIVIERT: Mitarbeiter muss qualifiziert sein
            //shiftMustBeAssignedOnce(constraintFactory), // Auskommentiert, da "macht delta" - muss später individuell debugged werden
            //atLeast11HoursRestTime(constraintFactory), // Auskommentiert, da "macht delta" - muss später individuell debugged werden
            cvdWeekendMustBeQualified(constraintFactory),
            adminShiftsOnlyForAdminQualified(constraintFactory),
            //blockMustBeFullyAssignedHard(constraintFactory), // Bleibt auskommentiert, da wir die Soft-Version zum Debuggen nutzen
            noMixedWeekendCvDShiftsForSameEmployee(constraintFactory),
            employeeCannotExceedWeeklyHoursHard(constraintFactory), // AKTIVIERT: Mitarbeiter darf Wochenstunden nicht überschreiten


            // --- DIAGNOSTISCHE SOFT CONSTRAINT: Unbesetzte Blöcke penalisiert als Soft ---
            blockMustBeFullyAssignedSoft(constraintFactory),

            // --- WEITERE SOFT CONSTRAINTS (Basierend auf deiner letzten Auswahl) ---
            minimizeTargetHoursDeviation(constraintFactory), // Bleibt AKTIVIERT, wie in deiner letzten Liste
            //penalizeUnplannedEmployees(constraintFactory), // Diese ist jetzt in blockMustBeFullyAssignedSoft integriert
            preferSpecific20hPatterns(constraintFactory),
            avoidMoreThanFiveConsecutiveDays(constraintFactory),
            avoidCvdDoubleOccupancy(constraintFactory),
            distributeCvdEarlyEvenly(constraintFactory),
            distributeCvdLateEvenly(constraintFactory),
            limitWeekendShiftsPerEmployee(constraintFactory),
            distributeEmployeesEvenlyAcrossCoreShifts(constraintFactory)
        };
    }

    // --- HARD CONSTRAINTS (Methoden-Definitionen) ---

    Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .join(
                    Schicht.class,
                    Joiners.equal(Schicht::getMitarbeiter),
                    Joiners.lessThan(Schicht::getId)
                )
                .filter((schicht1, schicht2) ->
                        schicht1.getStartDateTime().isBefore(schicht2.getEndDateTime()) &&
                        schicht2.getStartDateTime().isBefore(schicht1.getEndDateTime())
                )
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Employee cannot be in two places at the same time (temporal)");
    }

    // AKTIVIERT: Mitarbeiter muss für Schichtblock qualifiziert sein
    Constraint employeeMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                // Nutzt die aktualisierte hasAllQualifikationen-Methode, die List<String> akzeptiert
                .filter(schichtBlock -> !schichtBlock.getMitarbeiter().hasAllQualifikationen(schichtBlock.getRequiredQualifikationen()))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Employee must be qualified (SchichtBlock)");
    }

    Constraint shiftMustBeAssignedOnce(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .groupBy(
                        Function.identity(),
                        ConstraintCollectors.count()
                )
                .filter((schicht, count) -> count > 1)
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Individual shift must be assigned only once");
    }

    Constraint atLeast11HoursRestTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .join(
                    Schicht.class,
                    Joiners.equal(Schicht::getMitarbeiter),
                    Joiners.lessThan(Schicht::getId)
                )
                .filter((schicht1, schicht2) -> {
                    LocalDateTime start1 = schicht1.getStartDateTime();
                    LocalDateTime end1 = schicht1.getEndDateTime();
                    LocalDateTime start2 = schicht2.getStartDateTime();
                    LocalDateTime end2 = schicht2.getEndDateTime();

                    if (end1 == null || start2 == null || start1 == null || end2 == null) {
                        return false; // One of the times is null, no check possible
                    }

                    // Check if shifts overlap or immediately follow each other
                    // and rest time is violated
                    if (end1.isBefore(start2)) { // Shift 1 ends before Shift 2 begins
                        Duration duration = Duration.between(end1, start2);
                        return duration.toHours() < 11;
                    } else if (end2.isBefore(start1)) { // Shift 2 ends before Shift 1 begins
                        Duration duration = Duration.between(end2, start1);
                        return duration.toHours() < 11;
                    }
                    // If shifts overlap, it's already a violation of noOverlappingShifts.
                    // Here, we focus on rest time between non-overlapping shifts.
                    return false;
                })
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Employee must have at least 11 hours rest time between shifts");
    }

    Constraint cvdWeekendMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .filter(Schicht::isCvdWochenendShift)
                .filter(schicht -> schicht.getMitarbeiter() != null && !schicht.getMitarbeiter().hasQualification("CVD_AM_WOCHENENDE_QUALIFIKATION"))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Weekend CvD qualification missing");
    }

    Constraint adminShiftsOnlyForAdminQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .filter(schichtBlock -> !schichtBlock.getMitarbeiter().hasQualification("ADMIN_QUALIFIKATION"))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Admin shift by non-admin");
    }

    // ORIGINAL Hard Constraint (Umbenannt zur Klarheit, da wir jetzt eine Soft-Version nutzen)
    Constraint blockMustBeFullyAssignedHard(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() == null)
                .penalizeLong(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY),
                        SchichtBlock::getTotalDurationInMinutes)
                .asConstraint("Unassigned SchichtBlock (Hard)");
    }

    Constraint noMixedWeekendCvDShiftsForSameEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()) ||
                                         SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .groupBy(schichtBlock -> schichtBlock.getMitarbeiter(), // Group by employee
                         schichtBlock -> { // Group by the Saturday of the block's start week for weekend identification
                             // Assumes blockStartDatum is the Saturday for weekend blocks like createWeekendCvdFruehBlock
                             return schichtBlock.getBlockStartDatum().with(DayOfWeek.SATURDAY);
                         },
                         ConstraintCollectors.toSet(SchichtBlock::getBlockTyp)) // Collect the types of blocks assigned for that employee and weekend
                .filter((mitarbeiter, weekendSaturday, blockTypes) ->
                    blockTypes.contains(SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue()) &&
                    blockTypes.contains(SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue())
                )
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("CvD mixed early/late weekend shifts for same employee");
    }

    Constraint cvd7DayBlockIsExclusive(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .groupBy(SchichtBlock::getMitarbeiter,
                        ConstraintCollectors.toSet(Function.identity())) // Collect all blocks assigned to the employee
                .filter((mitarbeiter, assignedBlocks) -> {
                    long cvd7DayBlocksCount = assignedBlocks.stream()
                            .filter(sb -> SchichtTyp.CVD_FRUEH_7_TAGE_BLOCK.getDisplayValue().equals(sb.getBlockTyp()) ||
                                           SchichtTyp.CVD_SPAET_7_TAGE_BLOCK.getDisplayValue().equals(sb.getBlockTyp()))
                            .count();
                    // Hard Constraint violated if:
                    // 1. The employee has more than one 7-day CvD block (e.g., both early AND late 7-day block)
                    // OR
                    // 2. The employee has ONE 7-day CvD block AND other blocks in addition (set size > 1)
                    return cvd7DayBlocksCount > 1 || (cvd7DayBlocksCount == 1 && assignedBlocks.size() > 1);
                })
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("7-day CvD block is exclusive (max one 7-day CvD block and no other blocks)");
    }

    // AKTIVIERT: Hard Constraint: Mitarbeiter darf Wochenstunden nicht überschreiten
    Constraint employeeCannotExceedWeeklyHoursHard(ConstraintFactory constraintFactory) {
        // Annahme: Planung für 1 Woche, daher wochenstunden * 60 Minuten.
        // Passe 'numberOfPlanningWeeks' an, wenn dein Planungszeitraum variiert.
        int numberOfPlanningWeeks = 1; // Für eine Woche

        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getWochenstunden() > 0) // Nur Mitarbeiter mit festen Wochenstunden prüfen
                .filter(mitarbeiter -> {
                    // Hole die tatsächlich zugewiesenen Minuten über die neue Hilfsmethode
                    long totalAssignedMinutes = mitarbeiter.getTotalAssignedDurationInMinutes();
                    long targetMinutes = mitarbeiter.getWochenstunden() * 60L * numberOfPlanningWeeks;
                    return totalAssignedMinutes > targetMinutes; // Strafe, wenn zugewiesene Minuten Ziel überschreiten
                })
                .penalizeLong(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY),
                        (mitarbeiter) -> {
                            long totalAssignedMinutes = mitarbeiter.getTotalAssignedDurationInMinutes();
                            long targetMinutes = mitarbeiter.getWochenstunden() * 60L * numberOfPlanningWeeks;
                            // Die Strafe ist die Anzahl der Minuten, um die überschritten wurde
                            return (totalAssignedMinutes - targetMinutes);
                        })
                .asConstraint("Employee assigned more hours than weekly target (Hard)");
    }


    // --- SOFT CONSTRAINTS (Methoden-Definitionen) ---

    // Diagnostische Soft Constraint für unbesetzte Blöcke
    Constraint blockMustBeFullyAssignedSoft(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() == null) // Finde unbesetzte Blöcke
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_NOT_PLANNED_PER_EMPLOYEE), // Hohe Soft-Strafe
                        SchichtBlock::getTotalDurationInMinutes) // Strafe basiert auf der Dauer des unbesetzten Blocks
                .asConstraint("Unassigned SchichtBlock (Soft Penalty)");
    }

    // Bleibt AKTIVIERT, wie in deiner letzten Liste
    Constraint minimizeTargetHoursDeviation(ConstraintFactory constraintFactory) {
        int numberOfPlanningWeeks = 1;

        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getWochenstunden() > 0)
                // Nutzt direkt die neue Hilfsmethode des Mitarbeiters
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_TARGET_MINUTES_DEVIATION_PER_MINUTE),
                        (mitarbeiter) -> {
                            long totalMinutesWorked = mitarbeiter.getTotalAssignedDurationInMinutes();
                            long targetMinutes = mitarbeiter.getWochenstunden() * 60L * numberOfPlanningWeeks;
                            long deviation = Math.abs(totalMinutesWorked - targetMinutes);
                            return deviation * deviation; // Quadratische Strafe für Abweichung
                        })
                .asConstraint("Minimize deviation from target hours over planning period");
    }


    Constraint penalizeUnplannedEmployees(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getWochenstunden() > 0) // Only consider employees who should be scheduled
                .filter(mitarbeiter -> mitarbeiter.getAssignedSchichtBlocks() == null || mitarbeiter.getAssignedSchichtBlocks().isEmpty())
                .penalize(HardSoftLongScore.ofSoft(SOFT_PENALTY_NOT_PLANNED_PER_EMPLOYEE))
                .asConstraint("Penalize unplanned employees");
    }

    Constraint preferSpecific20hPatterns(ConstraintFactory constraintFactory) {
        String BLOCK_TYPE_20H_PATTERN = SchichtTyp.KERN_MO_FR_20H_BLOCK.getDisplayValue();

        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> schichtBlock.getBlockTyp().equals(BLOCK_TYPE_20H_PATTERN))
                .filter(schichtBlock -> schichtBlock.getMitarbeiter().getWochenstunden() != 20)
                .penalize(HardSoftLongScore.ofSoft(SOFT_PENALTY_PREFERENCE_PART_TIME_BLOCK))
                .asConstraint("Prefer 20h pattern: 20h block not assigned to 20h employee");
    }

    Constraint avoidMoreThanFiveConsecutiveDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getAssignedSchichtBlocks() != null && !mitarbeiter.getAssignedSchichtBlocks().isEmpty())
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_MORE_THAN_5_CONSECUTIVE_DAYS),
                        (mitarbeiter) -> {
                            List<Schicht> sortedShifts = mitarbeiter.getAssignedSchichtBlocks().stream()
                                    .flatMap(sb -> sb.getSchichtenImBlock().stream())
                                    .sorted(Comparator.comparing(Schicht::getDatum))
                                    .collect(Collectors.toList());

                            long consecutiveDaysPenalty = 0;
                            if (sortedShifts.isEmpty()) {
                                return 0L;
                            }

                            LocalDate lastShiftDate = null;
                            long currentConsecutiveDays = 0;

                            for (Schicht currentShift : sortedShifts) {
                                if (lastShiftDate == null || !currentShift.getDatum().isEqual(lastShiftDate.plusDays(1))) {
                                    currentConsecutiveDays = 1; // Start new streak if there's a gap or it's the first shift
                                } else {
                                    currentConsecutiveDays++; // Continue streak
                                }
                                lastShiftDate = currentShift.getDatum();

                                // If the number of consecutive days exceeds the limit
                                // Add the excess to the penalty.
                                if (currentConsecutiveDays > 5) {
                                    consecutiveDaysPenalty += (currentConsecutiveDays - 5);
                                }
                            }
                            return consecutiveDaysPenalty;
                        })
                .asConstraint("More than 5 consecutive days");
    }

    Constraint avoidCvdDoubleOccupancy(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .join(
                    Schicht.class,
                    Joiners.equal(Schicht::getMitarbeiter),
                    Joiners.equal(Schicht::getDatum),
                    Joiners.lessThan(Schicht::getId) // Prevents duplicate and self-pairs
                )
                .filter((schicht1, schicht2) -> {
                    // Check if both shifts are CvD types (early or late) based on individual shift types
                    boolean isCvdFrueh1 = SchichtTyp.CVD_FRUEHDIENST.getDisplayValue().equals(schicht1.getSchichtTyp());
                    boolean isCvdSpaet1 = SchichtTyp.CVD_SPAETDIENST.getDisplayValue().equals(schicht1.getSchichtTyp());

                    boolean isCvdFrueh2 = SchichtTyp.CVD_FRUEHDIENST.getDisplayValue().equals(schicht2.getSchichtTyp());
                    boolean isCvdSpaet2 = SchichtTyp.CVD_SPAETDIENST.getDisplayValue().equals(schicht2.getSchichtTyp());

                    // Filters pairs where one is early CvD and the other is late CvD
                    return (isCvdFrueh1 && isCvdSpaet2) || (isCvdSpaet1 && isCvdFrueh2);
                })
                .penalize(HardSoftLongScore.ofSoft(SOFT_PENALTY_CVD_DOUBLE_OCCUPANCY_DAY))
                .asConstraint("CvD double occupancy on the same day (early-late)");
    }

    Constraint distributeCvdEarlyEvenly(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock ->
                        SchichtTyp.CVD_FRUEHDIENST.getDisplayValue().equals(schichtBlock.getBlockTyp()) ||
                        SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()) ||
                        SchichtTyp.CVD_FRUEH_7_TAGE_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .groupBy(SchichtBlock::getMitarbeiter, ConstraintCollectors.count())
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_CVD_EVEN_DISTRIBUTION),
                        (mitarbeiter, numberOfEarlyShifts) -> (long) numberOfEarlyShifts * numberOfEarlyShifts)
                .asConstraint("Distribute CvD early shifts evenly");
    }

    Constraint distributeCvdLateEvenly(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock ->
                        SchichtTyp.CVD_SPAETDIENST.getDisplayValue().equals(schichtBlock.getBlockTyp()) ||
                        SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()) ||
                        SchichtTyp.CVD_SPAET_7_TAGE_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .groupBy(SchichtBlock::getMitarbeiter, ConstraintCollectors.count())
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_CVD_EVEN_DISTRIBUTION),
                        (mitarbeiter, numberOfLateShifts) -> (long) numberOfLateShifts * numberOfLateShifts)
                .asConstraint("Distribute CvD late shifts evenly");
    }

    Constraint limitWeekendShiftsPerEmployee(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .filter(schicht -> schicht.getMitarbeiter() != null)
                // Filter for shifts that are part of weekend CvD blocks or standard weekend shifts
                .filter(schicht -> schicht.isCvdWochenendShift() ||
                                   SchichtTyp.WOCHENEND_DIENST.getDisplayValue().equals(schicht.getSchichtTyp()))
                .groupBy(Schicht::getMitarbeiter, ConstraintCollectors.countDistinct(Schicht::getDatum))
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_WEEKEND_SHIFTS),
                        (mitarbeiter, numberOfWeekendDays) -> {
                            long idealWeekendDaysPerEmployee = 2L;
                            
                            // Check if the employee is assigned to any of the 7-day CvD blocks
                            boolean isAssignedTo7DayCvD = mitarbeiter.getAssignedSchichtBlocks().stream()
                                    .anyMatch(sb -> SchichtTyp.CVD_FRUEH_7_TAGE_BLOCK.getDisplayValue().equals(sb.getBlockTyp()) ||
                                                     SchichtTyp.CVD_SPAET_7_TAGE_BLOCK.getDisplayValue().equals(sb.getBlockTyp()));

                            if (isAssignedTo7DayCvD) {
                                // If already assigned to a 7-day CvD, penalize only days BEYOND the 7 days (or 2 weekend days within that block)
                                // This assumes the 7-day block already covers their primary weekend duty.
                                // For a 7-day block, it contains 2 weekend shifts. So, penalize if they have more than these 2.
                                return Math.max(0L, numberOfWeekendDays - 2L) * SOFT_PENALTY_WEEKEND_SHIFTS;
                            } else {
                                // For all other employees: Penalize days exceeding the ideal (e.g., 2 weekend days per week)
                                return Math.max(0L, numberOfWeekendDays - idealWeekendDaysPerEmployee) * SOFT_PENALTY_WEEKEND_SHIFTS;
                            }
                        })
                .asConstraint("Limit weekend shifts");
    }


    Constraint distributeEmployeesEvenlyAcrossCoreShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                // Corrected: Use the correct block type STANDARD_8H_DIENST for core shifts
                .filter(schichtBlock -> SchichtTyp.STANDARD_8H_DIENST.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .groupBy(SchichtBlock::getMitarbeiter, ConstraintCollectors.count())
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_CORE_SHIFT_EVEN_DISTRIBUTION),
                        (mitarbeiter, numberOfCoreShifts) -> (long) numberOfCoreShifts * numberOfCoreShifts)
                .asConstraint("Distribute core shifts evenly");
    }

}
