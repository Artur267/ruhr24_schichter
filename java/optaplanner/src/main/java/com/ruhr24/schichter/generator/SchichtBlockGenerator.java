// src/main/java/com/ruhr24.schichter.generator/SchichtBlockGenerator.java
package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.Ressort;
import com.ruhr24.schichter.domain.SchichtTyp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // Wichtig: Import für UUID
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class SchichtBlockGenerator {

    // schichtBlockIdCounter wurde entfernt, da wir jetzt UUID.randomUUID() verwenden

    public static final String CVD_QUALIFIKATION = "CVD_QUALIFIKATION";
    public static final String CVD_WOCHENENDE_QUALIFIKATION = "CVD_AM_WOCHENENDE_QUALIFIKATION";
    public static final String ONLINE_REDAKTION_QUALIFIKATION = "ONLINE_REDAKTION_QUALIFIKATION"; // Qualifikation für "Normaler Mitarbeiter"
    public static final String ADMIN_QUALIFIKATION = "ADMIN_QUALIFIKATION"; // Qualifikation für "Admin"

    public List<SchichtBlock> generateSchichtBlocks(LocalDate startDatum, LocalDate endDatum) {
        List<SchichtBlock> generatedBlocks = new ArrayList<>();
        // schichtBlockIdCounter = 1; // Counter wird nicht mehr benötigt

        LocalDate currentDay = startDatum;
        while (!currentDay.isAfter(endDatum)) {

            // Generiere die festen CvD Wochenschichten (Mo-Fr)
            // Diese Blöcke umfassen Mo-Fr. Sie werden nur einmal pro Woche generiert, wenn der currentDay ein Montag ist.
            if (currentDay.getDayOfWeek() == DayOfWeek.MONDAY) {
                // 1 CvD in der Woche 06:00 - 14:30 von Montag bis Freitag
                generatedBlocks.add(createCvDMoFrFruehBlock(
                        UUID.randomUUID(), currentDay, "CVD_FRUEHDIENST", List.of(CVD_QUALIFIKATION)));

                // 1 CvD in der Woche 14:30 - 23:00 von Montag bis Freitag
                generatedBlocks.add(createCvDMoFrSpaetBlock(
                        UUID.randomUUID(), currentDay, "CVD_SPAETDIENST", List.of(CVD_QUALIFIKATION)));

                // Admin-Block, falls er immer Mo-Fr besetzt sein muss
                int anzahlAdminBloeckeProWoche = 8; // Erhöht von 1 auf 4 als Startwert
                for (int i = 0; i < anzahlAdminBloeckeProWoche; i++) {
                    generatedBlocks.add(createAdminMoFrBlock(
                            UUID.randomUUID(), currentDay,
                            SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue(),
                            List.of(ADMIN_QUALIFIKATION)));
                }
            }

            // Kerndienste an Werktagen (Mo-Fr)
            if (currentDay.getDayOfWeek() != DayOfWeek.SATURDAY && currentDay.getDayOfWeek() != DayOfWeek.SUNDAY) {
                // Lege hier fest, wie viele STANDARD-Kerndienste es pro Werktag gibt.
                // PASSE DIESE ZAHL AN DEINEN BEDARF AN! Wenn du mehr oder weniger feste Kerndienste pro Tag hast.
                int anzahlStandardKerndiensteProWerktag = 45;
                for (int i = 0; i < anzahlStandardKerndiensteProWerktag; i++) {
                    generatedBlocks.add(create8HourShiftBlock(UUID.randomUUID(), currentDay,
                            "Standard-Kerndienst (" + currentDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + ") Slot " + (i + 1),
                            List.of(ONLINE_REDAKTION_QUALIFIKATION)));
                }
            } else { // Wochenend-Tage (Samstag/Sonntag)
                // WICHTIG: Hier generieren wir die Wochenend-CvDs als ZWEI-TAGE-BLÖCKE,
                // damit OptaPlanner versucht, eine Person für beide Tage zu finden.
                // Wenn du möchtest, dass sie einzeln vergeben werden können,
                // müsstest du createWochenendCvdFruehBlock/SpaetBlock (die Ein-Tages-Versionen) verwenden.

                // Wochenend CvD Frühdienst Block (Sa + So)
                generatedBlocks.add(createWeekendCvdFruehBlock(UUID.randomUUID(), currentDay,
                        "CVD_WOCHENENDE_FRUEHDIENST_BLOCK", // Konsistenter Name
                        List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));

                // Wochenend CvD Spätdienst Block (Sa + So)
                generatedBlocks.add(createWeekendCvdSpaetBlock(UUID.randomUUID(), currentDay,
                        "CVD_WOCHENENDE_SPAETDIENST_BLOCK", // Konsistenter Name
                        List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));

                // 2 Kerndienste am Wochenende pro Tag
                generatedBlocks.add(createWochenendDienstBlock(UUID.randomUUID(), currentDay,
                        "Wochenend-Kerndienst (" + currentDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + ") Slot 1",
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
                generatedBlocks.add(createWochenendDienstBlock(UUID.randomUUID(), currentDay,
                        "Wochenend-Kerndienst (" + currentDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + ") Slot 2",
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            currentDay = currentDay.plusDays(1);
        }

        // --- Optionale, wöchentliche Blöcke für Sonderfälle oder Teilzeitkräfte ---
        // Diese Blöcke werden nur hinzugefügt, wenn du sie explizit als feste Muster haben möchtest.
        // Für den Start würde ich diese AUSKOMMENTIERT LASSEN, bis der Hard Score auf 0 ist.
        // Wenn du sie aktivierst, stelle sicher, dass sie nicht mit den oben generierten Kerndiensten kollidieren.
        LocalDate weekLoopStart = startDatum;
        while (weekLoopStart.getDayOfWeek() != DayOfWeek.MONDAY && !weekLoopStart.isAfter(endDatum)) {
            weekLoopStart = weekLoopStart.minusDays(1);
            if (weekLoopStart.isBefore(startDatum.minusDays(7))) {
                weekLoopStart = startDatum.with(DayOfWeek.MONDAY);
                break;
            }
        }
        LocalDate tempWeekLoopCurrentDay = weekLoopStart;
        while (!tempWeekLoopCurrentDay.isAfter(endDatum.minusDays(6))) {

            // Beispiel: Ein Mitarbeiter, der jede Woche Mo-Do 32h arbeitet
            /*
            generatedBlocks.add(createKernMoDo32hBlock(
                    UUID.randomUUID(),
                    tempWeekLoopCurrentDay,
                    "KERN_MO_DO_32H_BLOCK",
                    List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            */

            // Beispiel: Ein Mitarbeiter, der jede Woche Mo-Fr 20h arbeitet (z.B. 4h pro Tag)
            /*
            generatedBlocks.add(createKernMoFr20hBlock(
                    UUID.randomUUID(),
                    tempWeekLoopCurrentDay,
                    "KERN_MO_FR_20H_BLOCK",
                    List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            */

            // Beispiel: Ein spezieller Wochenend-Kerndienst mit Ausgleichstagen (Block geht über 4 Tage: Sa, So, Mo, Di)
            // Du musst sicherstellen, dass hier der Samstag der aktuellen Woche genutzt wird.
            /*
            LocalDate samstagDerAktuellenWoche = tempWeekLoopCurrentDay.with(DayOfWeek.SATURDAY);
            if (!samstagDerAktuellenWoche.isAfter(endDatum)) {
                generatedBlocks.add(createWeekendKerndienstWithCompensatoryDaysBlock(
                        UUID.randomUUID(),
                        samstagDerAktuellenWoche,
                        "WOCHENEND_KERNDIENST_AUSGLEICH_BLOCK",
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }
            */
            // Beispiel: CvD 7-Tage-Block, der die täglichen Weekend-CvDs ersetzt.
            // Nur aktivieren, wenn du die täglichen Wochenend-CvDs oben dafür entfernst!
            /*
            generatedBlocks.add(createCvDWeekendFrueh7DayBlock(
                    UUID.randomUUID(),
                    tempWeekLoopCurrentDay,
                    "CVD_FRUEH_7_TAGE_BLOCK",
                    List.of(CVD_QUALIFIKATION)));

            generatedBlocks.add(createCvDWeekendSpaet7DayBlock(
                    UUID.randomUUID(),
                    tempWeekLoopCurrentDay,
                    "CVD_SPAET_7_TAGE_BLOCK",
                    List.of(CVD_QUALIFIKATION)));
            */

            tempWeekLoopCurrentDay = tempWeekLoopCurrentDay.plusWeeks(1);
        }

        System.out.println("[JAVA BACKEND] Generated " + generatedBlocks.size() + " total SchichtBlocks.");
        generatedBlocks.stream()
                .collect(Collectors.groupingBy(SchichtBlock::getBlockTyp, Collectors.counting()))
                .forEach((type, count) -> System.out.println("[JAVA BACKEND]   - " + type + ": " + count));

        System.out.println("[JAVA BACKEND] Generated " + generatedBlocks.size() + " SchichtBlocks.");
        return generatedBlocks;
    }

    // --- HELPER METHODS FÜR SCHICHTBLOCK-ERSTELLUNG ---
    // Diese Methoden sind notwendig, damit die oben aufgerufenen create-Methoden existieren.

    /**
     * Erstellt einen 8-Stunden-Dienst SchichtBlock (z.B. Kerndienst).
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param date Das Datum der Schicht.
     * @param name Der Name des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock create8HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.ACHT_STUNDEN_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "STANDARD_8H_DIENST", requiredQualifikationen);
    }

    /**
     * Erstellt einen Wochenend-Dienst SchichtBlock (z.B. Kerndienst am Wochenende).
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param date Das Datum der Schicht.
     * @param name Der Name des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createWochenendDienstBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.WOCHENEND_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "STANDARD_WOCHENEND_DIENST", requiredQualifikationen);
    }

    /**
     * Erstellt einen CvD Frühdienst Block, der von Montag bis Freitag geht.
     * Blocktyp: "CVD_MO_FR_FRUEHDIENST_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Der Montag der Woche, in der der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createCvDMoFrFruehBlock(UUID id, LocalDate startOfBlockWeek,
                                                 String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo (0) bis Fr (4)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(6, 0), LocalTime.of(14, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Mo-Fr Früh (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen CvD Spätdienst Block, der von Montag bis Freitag geht.
     * Blocktyp: "CVD_MO_FR_SPAETDIENST_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Der Montag der Woche, in der der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createCvDMoFrSpaetBlock(UUID id, LocalDate startOfBlockWeek,
                                                  String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo (0) bis Fr (4)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(14, 30), LocalTime.of(23, 0),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Mo-Fr Spät (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Admin-Block, der von Montag bis Freitag von 08:00 - 16:30 geht.
     * Blocktyp: "ADMIN_MO_FR_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     */
    private SchichtBlock createAdminMoFrBlock(UUID id, LocalDate startOfBlockWeek,
                                              String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo (0) bis Fr (4)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VERWALTUNG.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Admin Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Wochenend-CvD Frühdienst Block (Sa + So).
     * Blocktyp: "CVD_WOCHENENDE_FRUEHDIENST_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param saturdayOfBlockWeek Das Datum des Samstags, an dem der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createWeekendCvdFruehBlock(UUID id, LocalDate saturdayOfBlockWeek,
                                                        String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        // Samstag
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek, LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        // Sonntag
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek.plusDays(1), LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        return new SchichtBlock(id, "Wochenend CvD Früh (" + saturdayOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, saturdayOfBlockWeek, saturdayOfBlockWeek.plusDays(1), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Wochenend-CvD Spätdienst Block (Sa + So).
     * Blocktyp: "CVD_WOCHENENDE_SPAETDIENST_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param saturdayOfBlockWeek Das Datum des Samstags, an dem der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createWeekendCvdSpaetBlock(UUID id, LocalDate saturdayOfBlockWeek,
                                                        String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        // Samstag
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek, LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        // Sonntag
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek.plusDays(1), LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        return new SchichtBlock(id, "Wochenend CvD Spät (" + saturdayOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, saturdayOfBlockWeek, saturdayOfBlockWeek.plusDays(1), blockType, requiredQualifikationen);
    }

    // --- HELPER METHODS FÜR SPEZIELLE, WÖCHENTLICHE SCHICHTBLOCK-MUSTER (DERZEIT AUSKOMMENTIERT) ---
    // Diese Methoden sind in der Haupt-Generierungslogik auskommentiert,
    // können aber bei Bedarf wieder aktiviert werden.

    /**
     * Erstellt einen CvD Frühdienst Block, der über die gesamte Woche (Mo-So) geht.
     * Blocktyp: "CVD_FRUEH_7_TAGE_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Das Startdatum des Blocks (sollte ein Montag sein).
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createCvDWeekendFrueh7DayBlock(UUID id, LocalDate startOfBlockWeek,
                                                         String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo (0) bis So (6)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(6, 0), LocalTime.of(14, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Früh 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen CvD Spätdienst Block, der über die gesamte Woche (Mo-So) geht.
     * Blocktyp: "CVD_SPAET_7_TAGE_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Das Startdatum des Blocks (sollte ein Montag sein).
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createCvDWeekendSpaet7DayBlock(UUID id, LocalDate startOfBlockWeek,
                                                         String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo (0) bis So (6)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(14, 30), LocalTime.of(23, 0),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "CvD Spät 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Kerndienst Block, der von Mittwoch bis Freitag geht.
     * Blocktyp: "KERN_MITTWOCH_FREITAG_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param mittwochDatum Das Datum des Mittwochs, an dem der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKernMittwochFreitagBlock(UUID id, LocalDate mittwochDatum,
                                                       String blockType, List<String> requiredQualifikationen) {
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

    /**
     * Erstellt einen Kerndienst Block, der über die gesamte Woche (Mo-So) geht.
     * Blocktyp: "KERN_7_TAGE_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Der Montag der Woche, in der der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern7DayBlock(UUID id, LocalDate startOfBlockWeek,
                                             String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo (0) bis So (6)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst 7 Tage (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen speziellen CvD Block, der von Montag bis Sonntag geht.
     * Mo-Fr: Kerndienst 08:00 - 16:30
     * Sa & So: Entweder 06:00 - 14:30 (Früh) ODER 14:30 - 23:00 (Spät).
     * Blocktyp: "CVD_MOSO_WOCHENENDE_LEAD_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     * @param startOfBlockWeek Der Montag der Woche, in der der Block beginnt.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen (sollte CVD_QUALIFIKATION und CVD_AM_WOCHENENDE_QUALIFIKATION enthalten).
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createCvDMoSoWeekendLeadBlock(UUID id, LocalDate startOfBlockWeek,
                                                        String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();

        // Montag bis Freitag: Kerndienst 08:00 - 16:30
        for (int i = 0; i < 5; i++) { // Mo (0) bis Fr (4)
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }

        // Samstag: Frühdienst 06:00 - 14:30 ODER Spätdienst 14:30 - 23:00 (OptaPlanner wählt)
        LocalDate samstag = startOfBlockWeek.plusDays(5);
        schichten.add(new Schicht(UUID.randomUUID(), samstag, LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), samstag, LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));


        // Sonntag: Frühdienst 06:00 - 14:30 ODER Spätdienst 14:30 - 23:00 (OptaPlanner wählt)
        LocalDate sonntag = startOfBlockWeek.plusDays(6);
        schichten.add(new Schicht(UUID.randomUUID(), sonntag, LocalTime.of(6, 0), LocalTime.of(14, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), sonntag, LocalTime.of(14, 30), LocalTime.of(23, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), false));


        return new SchichtBlock(id, "CvD Mo-So Wochenende Lead (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(6), blockType, requiredQualifikationen);
    }

    /**
     * NEU: Erstellt einen Wochenend-Kerndienst-Block mit Ausgleichstagen am Montag und Dienstag der Folgewoche.
     * Blocktyp: "WOCHENEND_KERNDIENST_AUSGLEICH_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     */
    private SchichtBlock createWeekendKerndienstWithCompensatoryDaysBlock(UUID id, LocalDate saturdayOfBlockWeek,
                                                                          String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();

        // Samstag: Kerndienst 08:00 - 16:30
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));

        // Sonntag: Kerndienst 08:00 - 16:30
        schichten.add(new Schicht(UUID.randomUUID(), saturdayOfBlockWeek.plusDays(1), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));

        LocalDate blockEndDate = saturdayOfBlockWeek.plusDays(1); // Block endet Sonntag, die Ausgleichstage sind nur "impliziert" oder in anderen Blöcken abgebildet, falls nötig.
        // Wenn die Ausgleichstage selbst als freie Tage im System dargestellt werden müssen, dann müssen diese hier auch als Schichten mit "kein Mitarbeiter" oder "Urlaub" o.ä. modelliert werden.
        // Für den SchichtBlock selbst repräsentiert er die Tage, an denen gearbeitet wird.

        return new SchichtBlock(id, "WE Kerndienst + Ausgleich (" + saturdayOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, saturdayOfBlockWeek, blockEndDate, blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Block für einen Mitarbeiter mit 40 Wochenstunden (Mo-Fr Kerndienst).
     * Blocktyp: "KERN_MO_FR_40H_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     */
    private SchichtBlock createKernMoFr40hBlock(UUID id, LocalDate startOfBlockWeek,
                                                String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Fr 40h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Block für einen Mitarbeiter mit 32 Wochenstunden (Mo-Do Kerndienst).
     * Blocktyp: "KERN_MO_DO_32H_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     */
    private SchichtBlock createKernMoDo32hBlock(UUID id, LocalDate startOfBlockWeek,
                                                String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Do 32h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(3), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Block für einen Mitarbeiter mit 20 Wochenstunden.
     * Dies könnte z.B. 5x 4-Stunden-Schichten sein oder 2x 8h + 1x 4h.
     * Wir nehmen hier beispielhaft 5x 4-Stunden-Schichten (Mo-Fr).
     * Blocktyp: "KERN_MO_FR_20H_BLOCK"
     * @param id Die ID des SchichtBlocks (jetzt UUID).
     */
    private SchichtBlock createKernMoFr20hBlock(UUID id, LocalDate startOfBlockWeek,
                                                String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(12, 0),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.KERNDIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Fr 20h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    // --- HELPER METHODS FÜR FLEXIBLE EINZELSCHICHTEN (DERZEIT AUSKOMMENTIERT) ---
    // Diese Methoden sind in der Haupt-Generierungslogik auskommentiert,
    // da sie die Anzahl der Schichtblöcke stark erhöhen und die Planung erschweren könnten.


    private SchichtBlock create4HourEarlyShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(12, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_FRUEH.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "4H_EARLY_SHIFT", requiredQualifikationen);
    }

    private SchichtBlock create4HourLateShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(12, 0), LocalTime.of(16, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_SPAET.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "4H_LATE_SHIFT", requiredQualifikationen);
    }

    private SchichtBlock create5HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(13, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.FUENF_STUNDEN_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "5H_SHIFT", requiredQualifikationen);
    }

    private SchichtBlock create6HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(14, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.SECHS_STUNDEN_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "6H_SHIFT", requiredQualifikationen);
    }

    private SchichtBlock create7HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(15, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.SIEBEN_STUNDEN_SCHICHT.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "7H_SHIFT", requiredQualifikationen);
    }

    private SchichtBlock createWeiterbildungBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.WEITERBILDUNG.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, "WEITERBILDUNG_SHIFT", requiredQualifikationen);
    }
}

