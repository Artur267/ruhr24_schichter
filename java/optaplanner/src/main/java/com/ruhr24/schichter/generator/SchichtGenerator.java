package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Mitarbeiter;
//import com.ruhr24.schichter.domain.Ressort;
import com.ruhr24.schichter.domain.Schicht;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SchichtGenerator {

    // === HIER KANNST DU DEN TÄGLICHEN BEDARF STEUERN ===
    // Anzahl der verfügbaren Arbeitsplätze pro Typ an einem Wochentag (Mo-Fr)
    private static final int ANZAHL_8H_SLOTS = 44; // Für die vielen 40h-Kräfte
    private static final int ANZAHL_7H_SLOTS = 2;
    private static final int ANZAHL_6H_SLOTS = 5;
    private static final int ANZAHL_5H_SLOTS = 5;
    private static final int ANZAHL_4H_SLOTS_FRUEH = 1;
    private static final int ANZAHL_4H_SLOTS_SPAET = 1;

    // Anzahl der Kerndienste am Wochenende (Sa/So)
    private static final int ANZAHL_WOCHENEND_SLOTS = 2;


    /**
     * Erstellt eine vielfältige Liste von leeren Schichten für den gesamten Planungszeitraum.
     */
    public List<Schicht> generate(LocalDate von, LocalDate bis, List<Mitarbeiter> mitarbeiterList) {
        List<Schicht> schichten = new ArrayList<>();
        System.out.println("[JAVA BACKEND] Generator: Erstelle vielfältige Schicht-Slots von " + von + " bis " + bis);

        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            
            // 1. OBLIGATORISCH: CvD-Schichten für jeden Tag
            schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(6, 0), LocalTime.of(14, 30), "RUHR24.de", 1, "CvD Frühschicht", false));
            schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(14, 30), LocalTime.of(23, 0), "RUHR24.de", 1, "CVD Spätschicht", false));

            boolean isWeekend = tag.getDayOfWeek() == DayOfWeek.SATURDAY || tag.getDayOfWeek() == DayOfWeek.SUNDAY;

            if (isWeekend) {
                // 2. WOCHENENDE: Erzeuge nur die benötigten Wochenend-Dienste
                for (int i = 0; i < ANZAHL_WOCHENEND_SLOTS; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(16, 30), "RUHR24.de", 1, "Wochenend-Dienst", false));
                }
            } else {
                // 3. WOCHENTAG: Erzeuge den Pool an flexiblen Schichten
                for (int i = 0; i < ANZAHL_8H_SLOTS; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(16, 30), "RUHR24.de", 1, "8-Stunden-Dienst", false));
                }
                for (int i = 0; i < ANZAHL_7H_SLOTS; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(15, 0), "RUHR24.de", 1, "7-Stunden-Schicht", false));
                }
                for (int i = 0; i < ANZAHL_6H_SLOTS; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(14, 0), "RUHR24.de", 1, "6-Stunden-Dienst", false));
                }
                for (int i = 0; i < ANZAHL_5H_SLOTS; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(13, 0), "RUHR24.de", 1, "5-Stunden-Dienst", false));
                }
                for (int i = 0; i < ANZAHL_4H_SLOTS_FRUEH; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(8, 0), LocalTime.of(12, 0), "RUHR24.de", 1, "4-Stunden-Dienst (früh)", false));
                }
                for (int i = 0; i < ANZAHL_4H_SLOTS_SPAET; i++) {
                    schichten.add(new Schicht(UUID.randomUUID(), tag, LocalTime.of(12, 0), LocalTime.of(16, 0), "RUHR24.de", 1, "4-Stunden-Dienst (spät)", false));
                }
            }
        }
        
        System.out.println("[JAVA BACKEND] Generator: " + schichten.size() + " einzelne Schicht-Slots dynamisch erstellt.");
        return schichten;
    }
}