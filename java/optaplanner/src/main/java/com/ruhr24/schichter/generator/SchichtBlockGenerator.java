package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Ressort;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.SchichtTyp;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class SchichtBlockGenerator {

    public static final String CVD_QUALIFIKATION = "CVD_QUALIFIKATION";
    public static final String CVD_WOCHENENDE_QUALIFIKATION = "CVD_AM_WOCHENENDE_QUALIFIKATION";
    public static final String ONLINE_REDAKTION_QUALIFIKATION = "ONLINE_REDAKTION_QUALIFIKATION";
    public static final String ADMIN_QUALIFIKATION = "ADMIN_QUALIFIKATION";
    public static final String WERKSTUDENT_QUALIFIKATION = "WERKSTUDENT_QUALIFIKATION";

    /**
     * Die einzige und korrekte Methode zur Generierung von Schichtblöcken.
     * Akzeptiert Startdatum, Enddatum und die Liste der Mitarbeiter.
     */
    public List<SchichtBlock> generateSchichtBlocks(LocalDate startDatum, LocalDate endDatum, List<Mitarbeiter> mitarbeiterList) {
        List<SchichtBlock> generatedBlocks = new ArrayList<>();

        LocalDate currentWeekStart = startDatum.with(DayOfWeek.MONDAY);
        if (startDatum.getDayOfWeek() != DayOfWeek.MONDAY) {
            currentWeekStart = currentWeekStart.minusWeeks(1);
        }

        while (!currentWeekStart.isAfter(endDatum)) {
            // Wöchentliche Blöcke generieren
            generatedBlocks.add(createCvDMoFrFruehBlock(UUID.randomUUID(), currentWeekStart, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), List.of(CVD_QUALIFIKATION)));
            generatedBlocks.add(createCvDMoFrSpaetBlock(UUID.randomUUID(), currentWeekStart, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), List.of(CVD_QUALIFIKATION)));

            for (int i = 0; i < 2; i++) {
                generatedBlocks.add(createAdminMoFrBlock(UUID.randomUUID(), currentWeekStart, SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue(), List.of(ADMIN_QUALIFIKATION)));
            }
            for (int i = 0; i < 35; i++) {
                generatedBlocks.add(createKernMoFr40hBlock(UUID.randomUUID(), currentWeekStart, SchichtTyp.KERN_MO_FR_40H_BLOCK.getDisplayValue(), List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }
            for (int i = 0; i < 1; i++) {
                generatedBlocks.add(createKernMoDo32hBlock(UUID.randomUUID(), currentWeekStart, SchichtTyp.KERN_MO_DO_32H_BLOCK.getDisplayValue(), List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }
            for (int i = 0; i < 1; i++) {
                generatedBlocks.add(createKern30hBlock(UUID.randomUUID(), currentWeekStart, List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }
            for (int i = 0; i < 1; i++) {
                generatedBlocks.add(createKern24hBlock(UUID.randomUUID(), currentWeekStart, i, List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }
            for (int i = 0; i < 2; i++) {
                generatedBlocks.add(createKern20hBlock(UUID.randomUUID(), currentWeekStart, List.of(ONLINE_REDAKTION_QUALIFIKATION, WERKSTUDENT_QUALIFIKATION)));
            }
            for (int i = 0; i < 6; i++) {
                generatedBlocks.add(createKern19hBlock(UUID.randomUUID(), currentWeekStart, List.of(ONLINE_REDAKTION_QUALIFIKATION, WERKSTUDENT_QUALIFIKATION)));
            }
            for (int i = 0; i < 1; i++) {
                generatedBlocks.add(createKern15hBlock(UUID.randomUUID(), currentWeekStart, i, List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // Tägliche Schichten für die Woche generieren
            for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
                LocalDate currentDayInLoop = currentWeekStart.plusDays(dayOffset);
                if (currentDayInLoop.isAfter(endDatum) || currentDayInLoop.isBefore(startDatum)) {
                    continue; // Nur Tage innerhalb des Planungszeitraums berücksichtigen
                }

                if (currentDayInLoop.getDayOfWeek() == DayOfWeek.SATURDAY || currentDayInLoop.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    if (currentDayInLoop.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        generatedBlocks.add(createWeekendCvdFruehBlock(UUID.randomUUID(), currentDayInLoop, SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue(), List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));
                        generatedBlocks.add(createWeekendCvdSpaetBlock(UUID.randomUUID(), currentDayInLoop, SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue(), List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));
                    }
                    for (int i = 0; i < 2; i++) {
                        generatedBlocks.add(createWochenendDienstBlock(UUID.randomUUID(), currentDayInLoop, "Wochenend-Kerndienst Slot " + (i + 1), List.of(ONLINE_REDAKTION_QUALIFIKATION)));
                    }
                }
            }
            currentWeekStart = currentWeekStart.plusWeeks(1);
        }

        System.out.println("[JAVA BACKEND] Generated " + generatedBlocks.size() + " total SchichtBlocks.");
        generatedBlocks.stream()
                .collect(Collectors.groupingBy(SchichtBlock::getBlockTyp, Collectors.counting()))
                .forEach((type, count) -> System.out.println("[JAVA BACKEND]   - " + type + ": " + count));

        assign20hBlocksTo20hEmployees(generatedBlocks, mitarbeiterList);
        return generatedBlocks;
    }

    private void assign20hBlocksTo20hEmployees(List<SchichtBlock> allBlocks, List<Mitarbeiter> allMitarbeiter) {
        List<Mitarbeiter> twentyHourEmployees = allMitarbeiter.stream()
                .filter(m -> m.getWochenstunden() == 20)
                .collect(Collectors.toList());

        List<SchichtBlock> kern20hBlocks = allBlocks.stream()
                .filter(sb -> sb.getBlockTyp().equals(SchichtTyp.KERN_MO_FR_20H_BLOCK.getDisplayValue()))
                .filter(sb -> sb.getMitarbeiter() == null)
                .collect(Collectors.toList());

        twentyHourEmployees.sort(Comparator.comparing(Mitarbeiter::getId));
        kern20hBlocks.sort(Comparator.comparing(SchichtBlock::getId));

        int assignedCount = 0;
        for (int i = 0; i < Math.min(kern20hBlocks.size(), twentyHourEmployees.size()); i++) {
            SchichtBlock block = kern20hBlocks.get(i);
            Mitarbeiter employee = twentyHourEmployees.get(i);
            block.setMitarbeiter(employee);
            assignedCount++;
            System.out.println("[JAVA BACKEND] Initial zugewiesen: " + block.getName() + " an " + employee.getNachname());
        }
        System.out.println("[JAVA BACKEND] Initial " + assignedCount + " KERN_MO_FR_20H_BLOCKs an 20h-Mitarbeiter zugewiesen.");
    }

    /*
    private SchichtBlock create8HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = List.of(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), requiredQualifikationen);
    }
    */
    
    private SchichtBlock createWochenendDienstBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = List.of(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_WOCHENEND_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, SchichtTyp.STANDARD_WOCHENEND_DIENST.getDisplayValue(), requiredQualifikationen);
    }

    private SchichtBlock createCvDMoFrFruehBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(6, 0), LocalTime.of(14, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Mo-Fr Früh (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    private SchichtBlock createCvDMoFrSpaetBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(14, 30), LocalTime.of(23, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Mo-Fr Spät (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    private SchichtBlock createAdminMoFrBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VERWALTUNG.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Admin Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    private SchichtBlock createWeekendCvdFruehBlock(UUID id, LocalDate saturday, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = List.of(
            new Schicht(UUID.randomUUID(), saturday, LocalTime.of(6, 0), LocalTime.of(14, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false),
            new Schicht(UUID.randomUUID(), saturday.plusDays(1), LocalTime.of(6, 0), LocalTime.of(14, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false)
        );
        return new SchichtBlock(id, "Wochenend CvD Früh (" + saturday.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, saturday, saturday.plusDays(1), blockType, requiredQualifikationen);
    }

    private SchichtBlock createWeekendCvdSpaetBlock(UUID id, LocalDate saturday, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = List.of(
            new Schicht(UUID.randomUUID(), saturday, LocalTime.of(14, 30), LocalTime.of(23, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false),
            new Schicht(UUID.randomUUID(), saturday.plusDays(1), LocalTime.of(14, 30), LocalTime.of(23, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false)
        );
        return new SchichtBlock(id, "Wochenend CvD Spät (" + saturday.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, saturday, saturday.plusDays(1), blockType, requiredQualifikationen);
    }
    
    private SchichtBlock createKernMoFr40hBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Fr 40h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    private SchichtBlock createKernMoDo32hBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Do 32h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(3), blockType, requiredQualifikationen);
    }

    private SchichtBlock createKern30hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(8, 0), LocalTime.of(14, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.SECHS_STUNDEN_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kern 30h Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), SchichtTyp.KERN_30H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }
    
    private SchichtBlock createKern24hBlock(UUID id, LocalDate startOfBlockWeek, int dayOffset, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + i), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        }
        LocalDate blockStartDate = startOfBlockWeek.plusDays(dayOffset);
        return new SchichtBlock(id, "Kern 24h (" + blockStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, blockStartDate, blockStartDate.plusDays(2), SchichtTyp.KERN_24H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }
    
    private SchichtBlock createKern20hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(i), LocalTime.of(8, 0), LocalTime.of(12, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_FRUEH.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kern 20h Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), SchichtTyp.KERN_MO_FR_20H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    private SchichtBlock createKern19hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = List.of(
            new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false),
            new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.WEDNESDAY), LocalTime.of(8, 0), LocalTime.of(16, 30), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false),
            new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(11, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_FRUEH.getDisplayValue(), false)
        );
        return new SchichtBlock(id, "Kern 19h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, startOfBlockWeek.with(DayOfWeek.MONDAY), startOfBlockWeek.with(DayOfWeek.FRIDAY), SchichtTyp.KERN_19H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    private SchichtBlock createKern15hBlock(UUID id, LocalDate startOfBlockWeek, int dayOffset, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + i), LocalTime.of(8, 0), LocalTime.of(13, 0), Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.FUENF_STUNDEN_DIENST.getDisplayValue(), false));
        }
        LocalDate blockStartDate = startOfBlockWeek.plusDays(dayOffset);
        return new SchichtBlock(id, "Kern 15h (" + blockStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")", schichten, blockStartDate, blockStartDate.plusDays(2), SchichtTyp.KERN_15H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }



    // --- Deine anderen Methoden aus dem ursprünglichen Code ---
    // Diese Methoden bleiben unverändert, wurden aber im Hauptteil auskommentiert,
    // es sei denn, du entscheidest dich, sie zu reaktivieren.
    /*
    private SchichtBlock createKernMittwochFreitagBlock(UUID id, LocalDate mittwochDatum, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), mittwochDatum, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), mittwochDatum.plusDays(1), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), mittwochDatum.plusDays(2), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));

        return new SchichtBlock(id, "Kerndienst Mi-Fr (" + mittwochDatum.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, mittwochDatum, mittwochDatum.plusDays(2), blockType, requiredQualifikationen);
    }

    private SchichtBlock createKern7DayBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    private SchichtBlock createCvDMoSoWeekendLeadBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        LocalDate samstag = startOfBlockWeek.plusDays(5);
        schichten.add(new Schicht(UUID.randomUUID(), samstag, LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), samstag, LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        LocalDate sonntag = startOfBlockWeek.plusDays(6);
        schichten.add(new Schicht(UUID.randomUUID(), sonntag, LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), sonntag, LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        return new SchichtBlock(id, "CvD Mo-So Wochenende Lead (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    private SchichtBlock createWeekendKerndienstWithCompensatoryDaysBlock(UUID id, LocalDate saturdayOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek.plusDays(1), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        LocalDate blockEndDate = saturdayOfBlockWeek.plusDays(1);
        return new SchichtBlock(id, "WE Kerndienst + Ausgleich (" + saturdayOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, saturdayOfBlockWeek, blockEndDate, blockType, requiredQualifikationen);
    }

    private SchichtBlock createCvDWeekendFrueh7DayBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo (0) bis So (6)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(6, 0), LocalTime.of(14, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Früh 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    private SchichtBlock createCvDWeekendSpaet7DayBlock(UUID id, LocalDate startOfBlockWeek, String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo (0) bis So (6)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(14, 30), LocalTime.of(23, 0),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Spät 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }
    */
}
