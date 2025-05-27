package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.Ressort; // NEU: Importieren
import com.ruhr24.schichter.domain.SchichtTyp; // NEU: Importieren

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class SchichtBlockGenerator {

    private static long schichtBlockIdCounter = 1; // Startwert für die ID

    public List<SchichtBlock> generateSchichtBlocks(LocalDate startDatum, LocalDate endDatum) {
        List<SchichtBlock> generatedBlocks = new ArrayList<>();
        schichtBlockIdCounter = 1; // Counter für jede neue Generierung zurücksetzen

        List<Ressort> kerndienstRessorts = List.of(
                Ressort.RUHR24_DE,
                Ressort.SPORT,
                Ressort.BUZZ
                // Füge weitere Ressorts hinzu, die Kerndienste haben könnten
        );

        int maxPotentialDailyCoreServiceBlocksPerRessort = 20; // Genug Puffer

        LocalDate currentDay = startDatum;
        while (!currentDay.isAfter(endDatum)) {

            if (currentDay.getDayOfWeek() != DayOfWeek.SATURDAY && currentDay.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (Ressort ressort : kerndienstRessorts) {
                    for (int i = 0; i < maxPotentialDailyCoreServiceBlocksPerRessort; i++) {
                        List<Schicht> kerndienstSchichten = new ArrayList<>();
                        // HIER MUSS ES SchichtTyp.KERNDIENST.getDisplayValue() und ressort.getDisplayValue() SEIN!
                        kerndienstSchichten.add(new Schicht(currentDay, LocalTime.of(8, 0), LocalTime.of(16, 30),
                                   SchichtTyp.KERNDIENST.getDisplayValue(), ressort.getDisplayValue())); // <-- GENAU HIER ANPASSEN!

                        List<String> requiredQualifikationen = new ArrayList<>();
                        // Qualifikationen basierend auf dem Ressort
                        if (ressort == Ressort.RUHR24_DE || ressort == Ressort.SPORT || ressort == Ressort.BUZZ) {
                            requiredQualifikationen.add("Online-Redaktion");
                        }
                        if (ressort == Ressort.SPORT) {
                            requiredQualifikationen.add("Ressort: Sport"); // Spezifische Qualifikation für Sport
                        }
                        // Füge hier weitere spezifische Qualifikationen für andere Ressorts hinzu

                        generatedBlocks.add(new SchichtBlock(schichtBlockIdCounter++,
                                "Kerndienst " + ressort.getDisplayValue() + " " + currentDay.format(DateTimeFormatter.ISO_LOCAL_DATE) + " Slot " + (i + 1),
                                kerndienstSchichten,
                                currentDay, currentDay, SchichtTyp.KERNDIENST.getDisplayValue(), // blockType als String (Enum.getDisplayValue())
                                requiredQualifikationen)); // requiredQualifikationen als List<String>
                    }
                }
            }
            currentDay = currentDay.plusDays(1);
        }

        // --- Feste Wochenend-Blöcke ---
        LocalDate firstMondayForTwoWeekBlock = startDatum;
        while (firstMondayForTwoWeekBlock.getDayOfWeek() != DayOfWeek.MONDAY) {
            firstMondayForTwoWeekBlock = firstMondayForTwoWeekBlock.minusDays(1);
            if (firstMondayForTwoWeekBlock.isBefore(startDatum.minusWeeks(2))) {
                firstMondayForTwoWeekBlock = startDatum;
                break;
            }
        }

        // CvD Wochenende Früh Block
        generatedBlocks.add(createTwoWeekWeekendBlock(schichtBlockIdCounter++,
                "CvD WE Früh", firstMondayForTwoWeekBlock, SchichtTyp.CVD_WOCHENENDE_FRUEH.getDisplayValue(), // blockType als String
                SchichtTyp.CVD_FRUEHDIENST.getDisplayValue(), LocalTime.of(6, 0), LocalTime.of(14, 30), // schichtTyp als String
                Ressort.RUHR24_DE.getDisplayValue(), List.of("CVD_QUALIFIKATION"))); // ressort als String, Qualifikation als List<String>

        // CvD Wochenende Spät Block
        generatedBlocks.add(createTwoWeekWeekendBlock(schichtBlockIdCounter++,
                "CvD WE Spät", firstMondayForTwoWeekBlock, SchichtTyp.CVD_WOCHENENDE_SPAET.getDisplayValue(), // blockType als String
                SchichtTyp.CVD_SPAETDIENST.getDisplayValue(), LocalTime.of(14, 30), LocalTime.of(23, 0), // schichtTyp als String
                Ressort.RUHR24_DE.getDisplayValue(), List.of("CVD_QUALIFIKATION"))); // ressort als String, Qualifikation als List<String>

        // Kerndienst Wochenende 1
        generatedBlocks.add(createTwoWeekWeekendBlock(schichtBlockIdCounter++,
                "Kerndienst WE 1", firstMondayForTwoWeekBlock, SchichtTyp.STANDARD_WOCHENENDE.getDisplayValue(), // blockType als String
                SchichtTyp.KERNDIENST.getDisplayValue(), LocalTime.of(8, 0), LocalTime.of(16, 30), // schichtTyp als String
                Ressort.RUHR24_DE.getDisplayValue(), List.of("Online-Redaktion"))); // ressort als String, Qualifikation als List<String>

        // Kerndienst Wochenende 2
        generatedBlocks.add(createTwoWeekWeekendBlock(schichtBlockIdCounter++,
                "Kerndienst WE 2", firstMondayForTwoWeekBlock, SchichtTyp.STANDARD_WOCHENENDE.getDisplayValue(), // blockType als String
                SchichtTyp.KERNDIENST.getDisplayValue(), LocalTime.of(8, 0), LocalTime.of(16, 30), // schichtTyp als String
                Ressort.RUHR24_DE.getDisplayValue(), List.of("Online-Redaktion"))); // ressort als String, Qualifikation als List<String>

        System.out.println("[JAVA BACKEND] Generated " + generatedBlocks.size() + " SchichtBlocks.");
        return generatedBlocks;
    }

    private SchichtBlock createTwoWeekWeekendBlock(long id, String namePrefix, LocalDate startOfFirstWeek,
                                                   String blockType, String schichtTyp, LocalTime startTime, LocalTime endTime,
                                                   String ressort, List<String> requiredQualifikationen) {
        List<Schicht> schichten = new ArrayList<>();
        LocalDate blockStart = startOfFirstWeek;
        LocalDate blockEnd = startOfFirstWeek.plusWeeks(1).plusDays(6);

        for (int dayOffset = 2; dayOffset < 7; dayOffset++) {
            LocalDate day = startOfFirstWeek.plusDays(dayOffset);
            if (!day.isAfter(blockEnd)) {
                // Diese Aufrufe sind OK, da schichtTyp und ressort hier bereits Strings sind (Methodenparameter)
                schichten.add(new Schicht(day, startTime, endTime, schichtTyp, ressort));
            }
        }

        LocalDate startOfSecondWeek = startOfFirstWeek.plusWeeks(1);
        for (int dayOffset = 2; dayOffset < 7; dayOffset++) {
            LocalDate day = startOfSecondWeek.plusDays(dayOffset);
            if (!day.isAfter(blockEnd)) {
                // Diese Aufrufe sind ebenfalls OK.
                schichten.add(new Schicht(day, startTime, endTime, schichtTyp, ressort));
            }
        }

        return new SchichtBlock(id, namePrefix + " (" + startOfFirstWeek.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")",
                schichten, blockStart, blockEnd, blockType, requiredQualifikationen);
    }
}