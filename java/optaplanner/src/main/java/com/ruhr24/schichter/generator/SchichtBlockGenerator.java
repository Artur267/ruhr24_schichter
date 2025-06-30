package com.ruhr24.schichter.generator;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors; // Für Stream-Operationen am Ende

@Component
public class SchichtBlockGenerator {

    public static final String CVD_QUALIFIKATION = "CVD_QUALIFIKATION";
    public static final String CVD_WOCHENENDE_QUALIFIKATION = "CVD_AM_WOCHENENDE_QUALIFIKATION";
    public static final String ONLINE_REDAKTION_QUALIFIKATION = "ONLINE_REDAKTION_QUALIFIKATION";
    public static final String ADMIN_QUALIFIKATION = "ADMIN_QUALIFIKATION";
    public static final String WERKSTUDENT_QUALIFIKATION = "WERKSTUDENT_QUALIFIKATION"; // Neu hinzugefügt, falls benötigt

    public List<SchichtBlock> generateSchichtBlocks(LocalDate startDatum, LocalDate endDatum) {
        List<SchichtBlock> generatedBlocks = new ArrayList<>();

        // Finde den ersten Montag im Planungszeitraum (oder davor), um wochenweise zu iterieren
        LocalDate currentWeekStart = startDatum.with(DayOfWeek.MONDAY);
        if (currentWeekStart.isAfter(startDatum)) {
            currentWeekStart = currentWeekStart.minusWeeks(1); // Starte bei der Vorwoche, wenn startDatum nicht Montag ist
        }

        while (!currentWeekStart.isAfter(endDatum)) {
            // Wenn die aktuelle Woche außerhalb des gewünschten Planungszeitraums liegt, überspringen
            if (currentWeekStart.isAfter(endDatum.minusDays(6))) { // Wenn Montag der aktuellen Woche nach dem Ende der Planungsperiode liegt
                break;
            }

            // --- Feste WÖCHENTLICHE Blöcke (einmal pro Woche generieren, am Montag) ---
            // CvD Mo-Fr Frühdienst
            generatedBlocks.add(createCvDMoFrFruehBlock(
                    UUID.randomUUID(), currentWeekStart, SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(),
                    List.of(CVD_QUALIFIKATION)));

            // CvD Mo-Fr Spätdienst
            generatedBlocks.add(createCvDMoFrSpaetBlock(
                    UUID.randomUUID(), currentWeekStart, SchichtTyp.CVD_SPAETDIENST.getDisplayValue(),
                    List.of(CVD_QUALIFIKATION)));

            // Admin Mo-Fr Block (Beispiel: 2 Admin-Blöcke pro Woche)
            // Die Anzahl hier sollte widerspiegeln, wie viele ADMIN_MO_FR_BLOCKs du pro Woche benötigst.
            int anzahlAdminBloeckeProWoche = 2; // Realistischerer Wert
            for (int i = 0; i < anzahlAdminBloeckeProWoche; i++) {
                generatedBlocks.add(createAdminMoFrBlock(
                        UUID.randomUUID(), currentWeekStart,
                        SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue(),
                        List.of(ADMIN_QUALIFIKATION)));
            }

            // KERN_MO_FR_40H_BLOCK (Beispiel: Für 10 Vollzeit-Mitarbeiter mit festem Muster)
            int numKernMoFr40hBlocks = 30;
            for (int i = 0; i < numKernMoFr40hBlocks; i++) {
                generatedBlocks.add(createKernMoFr40hBlock(
                        UUID.randomUUID(), currentWeekStart,
                        SchichtTyp.KERN_MO_FR_40H_BLOCK.getDisplayValue(),
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // KERN_MO_DO_32H_BLOCK (Beispiel: Für 2 Mitarbeiter mit 32h)
            int numKernMoDo32hBlocks = 1;
            for (int i = 0; i < numKernMoDo32hBlocks; i++) {
                generatedBlocks.add(createKernMoDo32hBlock(
                        UUID.randomUUID(), currentWeekStart,
                        SchichtTyp.KERN_MO_DO_32H_BLOCK.getDisplayValue(),
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // NEU: KERN_30H_BLOCK (Beispiel: Für 1 Mitarbeiter mit 30h)
            int numKern30hBlocks = 1;
            for (int i = 0; i < numKern30hBlocks; i++) {
                generatedBlocks.add(createKern30hBlock(
                        UUID.randomUUID(), currentWeekStart,
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // NEU: KERN_24H_BLOCK (Beispiel: Für 2 Mitarbeiter mit 24h, mit Versatz)
            int numKern24hBlocks = 1;
            for (int i = 0; i < numKern24hBlocks; i++) {
                // Generiert Blöcke, die Montag, Dienstag, Mittwoch ODER Dienstag, Mittwoch, Donnerstag etc. starten können
                generatedBlocks.add(createKern24hBlock(
                        UUID.randomUUID(), currentWeekStart, i, // i als DayOffset
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // NEU: KERN_20H_BLOCK (Beispiel: Für 3 Werkstudenten mit 20h)
            // Beachte: Du hattest bereits KERN_MO_FR_20H_BLOCK. Ich habe createKern20hBlock basierend auf deiner Beschreibung erstellt.
            int numKern20hBlocks = 2;
            for (int i = 0; i < numKern20hBlocks; i++) {
                generatedBlocks.add(createKern20hBlock(
                        UUID.randomUUID(), currentWeekStart,
                        List.of(ONLINE_REDAKTION_QUALIFIKATION, WERKSTUDENT_QUALIFIKATION)));
            }

            // NEU: KERN_19H_BLOCK (Beispiel: Für 2 Werkstudenten mit 19h)
            int numKern19hBlocks = 6;
            for (int i = 0; i < numKern19hBlocks; i++) {
                generatedBlocks.add(createKern19hBlock(
                        UUID.randomUUID(), currentWeekStart,
                        List.of(ONLINE_REDAKTION_QUALIFIKATION, WERKSTUDENT_QUALIFIKATION)));
            }

            // NEU: KERN_15H_BLOCK (Beispiel: Für 1 Mitarbeiter mit 15h, mit Versatz)
            int numKern15hBlocks = 1;
            for (int i = 0; i < numKern15hBlocks; i++) {
                generatedBlocks.add(createKern15hBlock(
                        UUID.randomUUID(), currentWeekStart, i, // i als DayOffset
                        List.of(ONLINE_REDAKTION_QUALIFIKATION)));
            }

            // --- Tägliche Schichtgenerierung für die aktuelle Woche ---
            for (int dayOffset = 0; dayOffset < 7; dayOffset++) { // Iteriere Mo bis So
                LocalDate currentDayInLoop = currentWeekStart.plusDays(dayOffset);

                // Stelle sicher, dass der generierte Tag nicht nach dem endDatum liegt
                if (currentDayInLoop.isAfter(endDatum)) {
                    break;
                }

                if (currentDayInLoop.getDayOfWeek() != DayOfWeek.SATURDAY && currentDayInLoop.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    // Standard-Kerndienste an Werktagen
                    // Passe diese Zahl weiterhin an deinen *täglichen* Bedarf an, wenn du flexible Slots hast,
                    // zusätzlich zu den festen Wochenblöcken oben.
                    // Bei 40 pro Werktag (wie du es hattest), das sind 200 pro Woche, was sehr viel ist.
                    // Ein realistischerer Wert für *zusätzliche* flexible Schichten wäre z.B. 2-5 pro Tag,
                    // wenn die meisten Stunden durch die "Wochenblöcke" abgedeckt werden.
                    int anzahlStandardKerndiensteProWerktag = 5; // Realistischerer Wert für flexible 8h-Slots
                    for (int i = 0; i < anzahlStandardKerndiensteProWerktag; i++) {
                        generatedBlocks.add(create8HourShiftBlock(UUID.randomUUID(), currentDayInLoop,
                                "Standard-Kerndienst (" + currentDayInLoop.format(DateTimeFormatter.ISO_LOCAL_DATE) + ") Slot " + (i + 1),
                                List.of(ONLINE_REDAKTION_QUALIFIKATION)));
                    }
                } else { // Wochenend-Tage (Samstag/Sonntag)
                    // Wichtig: Wenn du CvD 7-Tage-Blöcke verwendest, die Sa/So abdecken,
                    // dann musst du diese hier eventuell auskommentieren, um Überlappungen zu vermeiden.
                    if (currentDayInLoop.getDayOfWeek() == DayOfWeek.SATURDAY) {
                        generatedBlocks.add(createWeekendCvdFruehBlock(UUID.randomUUID(), currentDayInLoop,
                                SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue(),
                                List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));

                        generatedBlocks.add(createWeekendCvdSpaetBlock(UUID.randomUUID(), currentDayInLoop,
                                SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue(),
                                List.of(CVD_QUALIFIKATION, CVD_WOCHENENDE_QUALIFIKATION)));
                    }


                    // Wochenend-Kerndienste
                    int anzahlWochenendKerndiensteProTag = 2;
                    for (int i = 0; i < anzahlWochenendKerndiensteProTag; i++) {
                        generatedBlocks.add(createWochenendDienstBlock(UUID.randomUUID(), currentDayInLoop,
                                "Wochenend-Kerndienst (" + currentDayInLoop.format(DateTimeFormatter.ISO_LOCAL_DATE) + ") Slot " + (i + 1),
                                List.of(ONLINE_REDAKTION_QUALIFIKATION)));
                    }
                }
            }

            // Zum nächsten Montag springen
            currentWeekStart = currentWeekStart.plusWeeks(1);
        }

        System.out.println("[JAVA BACKEND] Generated " + generatedBlocks.size() + " total SchichtBlocks.");
        generatedBlocks.stream()
                .collect(Collectors.groupingBy(SchichtBlock::getBlockTyp, Collectors.counting()))
                .forEach((type, count) -> System.out.println("[JAVA BACKEND]   - " + type + ": " + count));

        return generatedBlocks;
    }

    // --- HELPER METHODS FÜR SCHICHTBLOCK-ERSTELLUNG (Deine bestehenden und neuen) ---

    /**
     * Erstellt einen 8-Stunden-Dienst SchichtBlock (z.B. Kerndienst).
     * @param id Die ID des SchichtBlocks (UUID).
     * @param date Das Datum der Schicht.
     * @param name Der Name des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock create8HourShiftBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * Erstellt einen Wochenend-Dienst SchichtBlock (z.B. Kerndienst am Wochenende).
     * @param id Die ID des SchichtBlocks (UUID).
     * @param date Das Datum der Schicht.
     * @param name Der Name des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createWochenendDienstBlock(UUID id, LocalDate date, String name, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_WOCHENEND_DIENST.getDisplayValue(), false));
        return new SchichtBlock(id, name, schichten, date, date, SchichtTyp.STANDARD_WOCHENEND_DIENST.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * Erstellt einen CvD Frühdienst Block, der von Montag bis Freitag geht.
     * Blocktyp: "CVD_FRUEHDIENST" (wird im Blocknamen verwendet, Blocktyp in SchichtTyp sollte präziser sein)
     * @param id Die ID des SchichtBlocks (UUID).
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
     * Blocktyp: "CVD_SPAETDIENST" (wird im Blocknamen verwendet)
     * @param id Die ID des SchichtBlocks (UUID).
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
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
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
     * @param id Die ID des SchichtBlocks (UUID).
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
     * @param id Die ID des SchichtBlocks (UUID).
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

    /**
     * Erstellt einen Block für einen Mitarbeiter mit 40 Wochenstunden (Mo-Fr 8.5h Kerndienst).
     * Blocktyp: "KERN_MO_FR_40H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKernMoFr40hBlock(UUID id, LocalDate startOfBlockWeek,
                                                String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo bis Fr
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Fr 40h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), blockType, requiredQualifikationen);
    }

    /**
     * Erstellt einen Block für einen Mitarbeiter mit 32 Wochenstunden (Mo-Do 8.5h Kerndienst).
     * Blocktyp: "KERN_MO_DO_32H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param blockType Der Typ des Blocks.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKernMoDo32hBlock(UUID id, LocalDate startOfBlockWeek,
                                                String blockType, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // Mo bis Do
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kerndienst Mo-Do 32h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(3), blockType, requiredQualifikationen);
    }

    /**
     * NEU: Erstellt einen Block für 30 Wochenstunden (z.B. 5x 6-Stunden-Schichten Mo-Fr).
     * Blocktyp: "KERN_30H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern30hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo bis Fr
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(14, 0), // 6 Stunden
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.SECHS_STUNDEN_DIENST.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kern 30h Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), SchichtTyp.KERN_30H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * NEU: Erstellt einen Block für 24 Wochenstunden (z.B. 3x 8-Stunden-Schichten).
     * Blocktyp: "KERN_24H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param dayOffset Starttag relativ zum Montag (0=Mo, 1=Di, ...), um verschiedene Muster zu erzeugen.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern24hBlock(UUID id, LocalDate startOfBlockWeek, int dayOffset, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + 1), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + 2), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));

        LocalDate blockStartDate = startOfBlockWeek.plusDays(dayOffset);
        LocalDate blockEndDate = startOfBlockWeek.plusDays(dayOffset + 2);
        return new SchichtBlock(id, "Kern 24h (" + blockStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, blockStartDate, blockEndDate, SchichtTyp.KERN_24H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * Erstellt einen Block für 20 Wochenstunden (z.B. 5x 4-Stunden-Schichten Mo-Fr).
     * Blocktyp: "KERN_MO_FR_20H_BLOCK" (Dein bestehender 20h-Block)
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern20hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) { // Mo bis Fr
            LocalDate currentDay = startOfBlockWeek.plusDays(i);
            schichten.add(new Schicht(UUID.randomUUID(), currentDay, LocalTime.of(8, 0), LocalTime.of(12, 0), // 4 Stunden
                    Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_FRUEH.getDisplayValue(), false));
        }
        return new SchichtBlock(id, "Kern 20h Mo-Fr (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.plusDays(4), SchichtTyp.KERN_MO_FR_20H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * NEU: Erstellt einen Block für 19 Wochenstunden (z.B. 2x 8h + 1x 3h).
     * Blocktyp: "KERN_19H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern19hBlock(UUID id, LocalDate startOfBlockWeek, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.WEDNESDAY), LocalTime.of(8, 0), LocalTime.of(16, 30),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.STANDARD_8H_DIENST.getDisplayValue(), false));
        // Für 3 Stunden: 08:00 - 11:00 Uhr
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.with(DayOfWeek.FRIDAY), LocalTime.of(8, 0), LocalTime.of(11, 0),
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.VIER_STUNDEN_DIENST_FRUEH.getDisplayValue(), false)); // Nahe an 4h, oder neuen 3h Typ erstellen
        return new SchichtBlock(id, "Kern 19h (" + startOfBlockWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, startOfBlockWeek, startOfBlockWeek.with(DayOfWeek.FRIDAY), SchichtTyp.KERN_19H_BLOCK.getDisplayValue(), requiredQualifikationen);
    }

    /**
     * NEU: Erstellt einen Block für 15 Wochenstunden (z.B. 3x 5-Stunden-Schichten).
     * Blocktyp: "KERN_15H_BLOCK"
     * @param id Die ID des SchichtBlocks (UUID).
     * @param startOfBlockWeek Der Montag der Woche.
     * @param dayOffset Starttag relativ zum Montag.
     * @param requiredQualifikationen Die benötigten Qualifikationen.
     * @return Der generierte SchichtBlock.
     */
    private SchichtBlock createKern15hBlock(UUID id, LocalDate startOfBlockWeek, int dayOffset, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset), LocalTime.of(8, 0), LocalTime.of(13, 0), // 5 Stunden
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.FUENF_STUNDEN_DIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + 1), LocalTime.of(8, 0), LocalTime.of(13, 0), // 5 Stunden
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.FUENF_STUNDEN_DIENST.getDisplayValue(), false));
        schichten.add(new Schicht(UUID.randomUUID(), startOfBlockWeek.plusDays(dayOffset + 2), LocalTime.of(8, 0), LocalTime.of(13, 0), // 5 Stunden
                Ressort.RUHR24_DE.getDisplayValue(), 1, SchichtTyp.FUENF_STUNDEN_DIENST.getDisplayValue(), false));

        LocalDate blockStartDate = startOfBlockWeek.plusDays(dayOffset);
        LocalDate blockEndDate = startOfBlockWeek.plusDays(dayOffset + 2);
        return new SchichtBlock(id, "Kern 15h (" + blockStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, blockStartDate, blockEndDate, SchichtTyp.KERN_15H_BLOCK.getDisplayValue(), requiredQualifikationen);
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
