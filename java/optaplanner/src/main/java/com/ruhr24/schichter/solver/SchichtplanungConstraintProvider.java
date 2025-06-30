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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    // HARD Constraint Weights (violations make the solution invalid)
    private static final long HARD_SCORE_PENALTY = 1L;


    // SOFT Constraint Weights (violations make the solution worse, but not invalid)
    // Weights increased to enforce fairer distribution.
    private static final long SOFT_PENALTY_TARGET_MINUTES_DEVIATION_PER_MINUTE = 2000L;
    private static final long SOFT_PENALTY_PREFERENCE_PART_TIME_BLOCK = 2000L;
    private static final long SOFT_PENALTY_NOT_PLANNED_PER_EMPLOYEE = 50000L;
    private static final long SOFT_PENALTY_MORE_THAN_5_CONSECUTIVE_DAYS = 50L;
    private static final long SOFT_PENALTY_CVD_DOUBLE_OCCUPANCY_DAY = 100L;
    private static final long SOFT_PENALTY_CVD_EVEN_DISTRIBUTION = 100L;
    private static final long SOFT_PENALTY_WEEKEND_SHIFTS = 50L;
    private static final long SOFT_PENALTY_CORE_SHIFT_EVEN_DISTRIBUTION = 50L;


    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            // --- HARD CONSTRAINTS ---
            cvd7DayBlockIsExclusive(constraintFactory),
            noOverlappingShifts(constraintFactory),
            employeeMustBeQualified(constraintFactory),
            shiftMustBeAssignedOnce(constraintFactory),
            atLeast11HoursRestTime(constraintFactory),
            cvdWeekendMustBeQualified(constraintFactory),
            adminShiftsOnlyForAdminQualified(constraintFactory),
            blockMustBeFullyAssigned(constraintFactory),
            noMixedWeekendCvDShiftsForSameEmployee(constraintFactory), // NEW HARD CONSTRAINT

            // --- SOFT CONSTRAINTS ---
            minimizeTargetHoursDeviation(constraintFactory),
            penalizeUnplannedEmployees(constraintFactory),
            preferSpecific20hPatterns(constraintFactory),
            avoidMoreThanFiveConsecutiveDays(constraintFactory),
            avoidCvdDoubleOccupancy(constraintFactory),
            distributeCvdEarlyEvenly(constraintFactory),
            distributeCvdLateEvenly(constraintFactory),
            limitWeekendShiftsPerEmployee(constraintFactory),
            distributeEmployeesEvenlyAcrossCoreShifts(constraintFactory)
        };
    }

    // --- HARD CONSTRAINTS ---

    /**
     * Hard Constraint: An employee cannot be in multiple places at the same time.
     * Checks if individual shifts within assigned SchichtBlocks overlap.
     * Uses LocalDateTime for precise overlap checking.
     */
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

    /**
     * Hard Constraint: An employee must possess all qualifications for the assigned SchichtBlock.
     */
    Constraint employeeMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> !schichtBlock.getMitarbeiter().hasAllQualifikationen(schichtBlock.getRequiredQualifikationen()))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Employee must be qualified (SchichtBlock)");
    }

    /**
     * Hard Constraint: Each individual shift must be assigned only once.
     * Prevents a shift, even if part of different blocks, from being assigned to multiple employees.
     */
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

    /**
     * Hard Constraint: Employees must have a minimum rest period of 11 hours between shifts.
     * This is a legal requirement in Germany.
     */
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

    /**
     * Hard Constraint: Weekend CvD shifts require the specific qualification "CVD_AM_WOCHENENDE_QUALIFIKATION".
     */
    Constraint cvdWeekendMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .flattenLast(SchichtBlock::getSchichtenImBlock)
                .filter(Schicht::isCvdWochenendShift)
                .filter(schicht -> schicht.getMitarbeiter() != null && !schicht.getMitarbeiter().hasQualification("CVD_AM_WOCHENENDE_QUALIFIKATION"))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Weekend CvD qualification missing");
    }

    /**
     * Hard Constraint: Admin shifts can only be assigned to employees with "ADMIN_QUALIFIKATION".
     */
    Constraint adminShiftsOnlyForAdminQualified(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue().equals(schichtBlock.getBlockTyp()))
                .filter(schichtBlock -> !schichtBlock.getMitarbeiter().hasQualification("ADMIN_QUALIFIKATION"))
                .penalize(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY))
                .asConstraint("Admin shift by non-admin");
    }

    /**
     * Hard Constraint: Every generated SchichtBlock must be assigned to an employee.
     * This ensures that all required shifts are filled.
     */
    Constraint blockMustBeFullyAssigned(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() == null)
                .penalizeLong(HardSoftLongScore.ofHard(HARD_SCORE_PENALTY),
                        SchichtBlock::getTotalDurationInMinutes)
                .asConstraint("Unassigned SchichtBlock");
    }

    /**
     * NEW HARD CONSTRAINT: A CvD employee must not be assigned both a weekend early CvD block
     * AND a weekend late CvD block for the same weekend.
     * This prevents mixed early/late shifts for the same CvD across a single weekend.
     */
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

    /**
     * Hard Constraint: A 7-day CvD block (early or late) is exclusive.
     * An employee assigned to a 7-day CvD block must NOT be assigned to any other SchichtBlocks
     * within the same planning period. This ensures the CvD weekly role is absolutely exclusive.
     */
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


    // --- SOFT CONSTRAINTS ---

    /**
     * Soft Constraint: Minimize deviation from target hours per employee.
     * Each employee should be as close as possible to their weekly hours.
     * Penalties are quadratic to penalize larger deviations more heavily.
     */
    Constraint minimizeTargetHoursDeviation(ConstraintFactory constraintFactory) {
        // Assume 1 planning week based on your output
        int numberOfPlanningWeeks = 1;

        return constraintFactory.forEach(Mitarbeiter.class)
                // Filter out employees with no weekly hours, as they likely shouldn't be scheduled
                .filter(mitarbeiter -> mitarbeiter.getWochenstunden() > 0)
                // Join SchichtBlocks only if they are assigned to a valid employee (mitarbeiter != null)
                .join(SchichtBlock.class,
                      Joiners.equal(Mitarbeiter::getId, schichtBlock -> schichtBlock.getMitarbeiter() != null ? schichtBlock.getMitarbeiter().getId() : null))
                .groupBy((mitarbeiter, schichtBlock) -> mitarbeiter,
                        ConstraintCollectors.sumLong((mitarbeiter, schichtBlock) -> schichtBlock.getTotalDurationInMinutes()))
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_TARGET_MINUTES_DEVIATION_PER_MINUTE),
                        (mitarbeiter, totalMinutesWorked) -> {
                            long targetMinutes = mitarbeiter.getWochenstunden() * 60L * numberOfPlanningWeeks;
                            long deviation = Math.abs(totalMinutesWorked - targetMinutes);
                            return deviation * deviation; // Quadratic penalty
                        })
                .asConstraint("Minimize deviation from target hours over planning period");
    }

    /**
     * NEW: Soft Constraint: Penalizes employees who are assigned 0 hours but have weekly hours > 0.
     * This constraint ensures that all employees with an active contract (weekly hours > 0)
     * are assigned at least some work if the solver has found a feasible solution.
     */
    Constraint penalizeUnplannedEmployees(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getWochenstunden() > 0) // Only consider employees who should be scheduled
                .filter(mitarbeiter -> mitarbeiter.getAssignedSchichtBlocks() == null || mitarbeiter.getAssignedSchichtBlocks().isEmpty())
                .penalize(HardSoftLongScore.ofSoft(SOFT_PENALTY_NOT_PLANNED_PER_EMPLOYEE))
                .asConstraint("Penalize unplanned employees");
    }

    /**
     * Prefers assigning the specific 20h pattern block to employees with 20 weekly hours.
     * Penalizes:
     * 1. A 20h pattern block is assigned to a NON-20h employee.
     */
    Constraint preferSpecific20hPatterns(ConstraintFactory constraintFactory) {
        // Ensure this block type exists in your generator
        String BLOCK_TYPE_20H_PATTERN = SchichtTyp.KERN_MO_FR_20H_BLOCK.getDisplayValue();

        return constraintFactory.forEach(SchichtBlock.class)
                .filter(schichtBlock -> schichtBlock.getMitarbeiter() != null)
                .filter(schichtBlock -> schichtBlock.getBlockTyp().equals(BLOCK_TYPE_20H_PATTERN))
                .filter(schichtBlock -> schichtBlock.getMitarbeiter().getWochenstunden() != 20)
                .penalize(HardSoftLongScore.ofSoft(SOFT_PENALTY_PREFERENCE_PART_TIME_BLOCK))
                .asConstraint("Prefer 20h pattern: 20h block not assigned to 20h employee");
    }


    /**
     * Soft Constraint: An employee should not work more than 5 consecutive days.
     */
    Constraint avoidMoreThanFiveConsecutiveDays(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Mitarbeiter.class)
                .filter(mitarbeiter -> mitarbeiter.getAssignedSchichtBlocks() != null && !mitarbeiter.getAssignedSchichtBlocks().isEmpty())
                .penalizeLong(HardSoftLongScore.ofSoft(SOFT_PENALTY_MORE_THAN_5_CONSECUTIVE_DAYS),
                        (mitarbeiter) -> {
                            // Collect all individual shifts for the employee and sort them by date
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


    /**
     * Soft Constraint: A CvD employee should not have consecutive shifts (early and late service) on the same day.
     * (Corrected to only check individual shift types, not block types for Schicht::getSchichtTyp)
     */
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

    /**
     * Soft Constraint: Distribute CvD early shifts evenly among qualified employees.
     * Penalizes if an employee has too many CvD early shifts in the planning period.
     */
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

    /**
     * Soft Constraint: Distribute CvD late shifts evenly among qualified employees.
     * Penalizes if an employee has too many CvD late shifts in the planning period.
     */
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

    /**
     * Soft Constraint: Limits the number of weekend shifts per employee.
     * A penalty for each weekend day assigned above a certain maximum.
     */
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


    /**
     * Soft Constraint: Attempts to distribute standard core shifts evenly among all qualified online editors.
     */
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
