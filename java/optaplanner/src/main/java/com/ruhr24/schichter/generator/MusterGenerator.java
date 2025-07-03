package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Arbeitsmuster;
import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MusterGenerator {

    /* DIESE SCHICHTEN HABEN WIR IN FACTORIAL
        Name der Schicht        Stundenspanne
        4-Stunden-Dienst (früh) 08:00 - 12:00
        4-Stunden-Dienst (spät) 12:00 - 16:00
        5-Stunden-Dienst        08:00 - 13:00
        6-Stunden-Dienst        08:00 - 14:00
        7-Stunden-Schicht       08:00 - 15:00
        8-Stunden-Dienst        08:00 - 16:30
        CvD Frühschicht         06:00 - 14:30
        CVD Spätschicht         14:30 - 23:00
        Weiterbildung           08:00 - 16:30
        Wochenend-Dienst        08:00 - 16:30 
    */
    private static final int ADMIN_WOCHEN_PRO_WOCHE = 10;
    private static final int REDAKTION_WOCHEN_PRO_WOCHE = 16;
    private static final int REDAKTION_WE_BLOECKE_PRO_MONAT = 5;
    private static final int CVD_KERN_WOCHEN_PRO_WOCHE = 11;
    private static final int CVD_FRUEH_WOCHEN_PRO_WOCHE = 1;
    private static final int CVD_SPAET_WOCHEN_PRO_WOCHE = 1;
    private static final int CVD_WE_FRUEH_PRO_WOCHE = 1;
    private static final int CVD_WE_SPAET_PRO_WOCHE = 1;
    private static final int TZ_30H_WOCHEN_PRO_WOCHE = 1;
    private static final int TZ_24H_WOCHEN_PRO_WOCHE = 1;
    private static final int TZ_20H_WOCHEN_PRO_WOCHE = 2;
    private static final int TZ_19H_WOCHEN_PRO_WOCHE = 4;
    private static final int TZ_15H_WOCHEN_PRO_WOCHE = 2;
    private static final int TZ_10H_WOCHEN_PRO_WOCHE = 1;


    public List<Arbeitsmuster> generate(LocalDate von, LocalDate bis, List<Mitarbeiter> mitarbeiterList) {
        List<Arbeitsmuster> musterPool = new ArrayList<>();
        LocalDate startWoche = von.with(DayOfWeek.MONDAY);
        
        boolean danieleWochenendeGeplant = false;
        int anzahlErstellterRedaktionWEBloecke = 0;
        LocalDate startDerWocheFuerDaniele = von.with(DayOfWeek.MONDAY);
            while (startDerWocheFuerDaniele.isBefore(bis)) {
                int woche = startDerWocheFuerDaniele.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());

                if (!danieleWochenendeGeplant) {
                    musterPool.add(createDanieleWochenendWoche1(woche, startDerWocheFuerDaniele));
                    startDerWocheFuerDaniele = startDerWocheFuerDaniele.plusWeeks(1);
                    woche = startDerWocheFuerDaniele.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
                    musterPool.add(createDanieleWochenendWoche2(woche, startDerWocheFuerDaniele));
                    danieleWochenendeGeplant = true;
                } else {
                    musterPool.add(createDanieleNormalwoche(woche, startDerWocheFuerDaniele));
                }
                startDerWocheFuerDaniele = startDerWocheFuerDaniele.plusWeeks(1);
            }

        while (startWoche.isBefore(bis)) {
            int wocheDesJahres = startWoche.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
            
            for (int i = 0; i < ADMIN_WOCHEN_PRO_WOCHE; i++) musterPool.add(createAdminWoche(wocheDesJahres, startWoche));
            for (int i = 0; i < CVD_KERN_WOCHEN_PRO_WOCHE; i++) musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_KERN"));
            musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_FRUEH"));
            musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_SPAET"));
            
            musterPool.add(createCvdWochenende(wocheDesJahres, startWoche, "CVD_WE_FRUEH"));
            musterPool.add(createCvdWochenende(wocheDesJahres, startWoche, "CVD_WE_SPAET"));

            for (int i = 0; i < REDAKTION_WOCHEN_PRO_WOCHE; i++) {
                musterPool.add(createRedaktionWoche(wocheDesJahres, startWoche));
            }

            musterPool.add(createLisaBenderWoche(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche30h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche24h(wocheDesJahres, startWoche));
            for (int i = 0; i < TZ_20H_WOCHEN_PRO_WOCHE; i++) musterPool.add(createTeilzeitWoche20h(wocheDesJahres, startWoche));
            for (int i = 0; i < TZ_19H_WOCHEN_PRO_WOCHE; i++) musterPool.add(createTeilzeitWoche19h(wocheDesJahres, startWoche));
            for (int i = 0; i < TZ_15H_WOCHEN_PRO_WOCHE; i++) musterPool.add(createTeilzeitWoche15h(wocheDesJahres, startWoche));
            for (int i = 0; i < TZ_10H_WOCHEN_PRO_WOCHE; i++) musterPool.add(createTeilzeitWoche10h(wocheDesJahres, startWoche));

            /*
            boolean danieleWochenendeGeplant = false;
            LocalDate startDerWocheFuerDaniele = von.with(DayOfWeek.MONDAY);
            while (startDerWocheFuerDaniele.isBefore(bis)) {
                int woche = startDerWocheFuerDaniele.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());

                if (!danieleWochenendeGeplant) {
                    musterPool.add(createDanieleWochenendWoche1(woche, startDerWocheFuerDaniele));
                    startDerWocheFuerDaniele = startDerWocheFuerDaniele.plusWeeks(1);
                    woche = startDerWocheFuerDaniele.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
                    musterPool.add(createDanieleWochenendWoche2(woche, startDerWocheFuerDaniele));
                    danieleWochenendeGeplant = true;
                } else {
                    musterPool.add(createDanieleNormalwoche(woche, startDerWocheFuerDaniele));
                }
                startDerWocheFuerDaniele = startDerWocheFuerDaniele.plusWeeks(1);
            } 
            */
            if (anzahlErstellterRedaktionWEBloecke < REDAKTION_WE_BLOECKE_PRO_MONAT) {
                if (startWoche.plusWeeks(1).isBefore(bis)) { // Nur wenn noch eine Folgewoche da ist
                    musterPool.add(createRedaktionWochenendWoche(wocheDesJahres, startWoche));
                    musterPool.add(createRedaktionAusgleichWoche(wocheDesJahres + 1, startWoche.plusWeeks(1)));
                    musterPool.add(createRedaktionWochenendWoche(wocheDesJahres, startWoche));
                    musterPool.add(createRedaktionAusgleichWoche(wocheDesJahres + 1, startWoche.plusWeeks(1)));
                    anzahlErstellterRedaktionWEBloecke++;
                    // Springe auch hier eine Woche weiter
                    startWoche = startWoche.plusWeeks(1);
                    continue;
                }
            }
            
            startWoche = startWoche.plusWeeks(1);
        }
        return musterPool;
    }

    // --- HILFSMETHODEN ---

    private Schicht createSchicht(LocalDate datum, String start, String ende, String typ) {
        return new Schicht(UUID.randomUUID(), datum, LocalTime.parse(start), LocalTime.parse(ende), "Bedarf", 1, typ, false);
    }
    
    private Arbeitsmuster createLisaBenderWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        // Mo-Do, 5h-Schicht
        for (int i = 0; i < 4; i++) {
            // Nimmt die 5-Stunden-Dienst Zeit aus deinem Kommentar
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "13:00:00", "5h-Dienst"));
        }
        // Der musterTyp ist das "Etikett", das zur Qualifikation passt
        return new Arbeitsmuster("LIBE_SONDERDIENST", 20, woche, schichten);
    }

    private Arbeitsmuster createRedaktionWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        // Mo-Fr, 8-Stunden-Dienst
        for (int i = 0; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KERNDIENST_REDAKTION"));
        }
        // Wichtig: Der musterTyp passt zur Qualifikation "REDAKTION"
        return new Arbeitsmuster("REDAKTION_40H", 40, woche, schichten);
    }

    private Arbeitsmuster createRedaktionWochenendWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 7; i++) { // Mo-So
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KERNDIENST_WE_REDAKTION"));
        }
        // Eindeutiger Typ für die Regel und Stundenzahl für die spätere Berechnung
        return new Arbeitsmuster("REDAKTION_WE_WOCHE_1", 56, woche, schichten);
    }

    private Arbeitsmuster createRedaktionAusgleichWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        // Mi, Do, Fr
        for (int i = 2; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KERNDIENST_AUSGLEICH_REDAKTION"));
        }
        return new Arbeitsmuster("REDAKTION_AUSGLEICH_WOCHE_2", 24, woche, schichten);
    }

    // KORREKTUR: Alle Methoden übergeben jetzt die Wochennummer an den Arbeitsmuster-Konstruktor
    private Arbeitsmuster createAdminWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "Admin-Dienst"));
        return new Arbeitsmuster("ADMIN_40H", 40, woche, schichten);
    }
    
    private Arbeitsmuster createCvdWoche(int woche, LocalDate start, String typ) {
        List<Schicht> schichten = new ArrayList<>();
        LocalTime startZeit = LocalTime.of(8,00), endZeit = LocalTime.of(16,30);
        if (typ.equals("CVD_FRUEH")) { startZeit = LocalTime.of(6,0); endZeit = LocalTime.of(14,30); }
        if (typ.equals("CVD_SPAET")) { startZeit = LocalTime.of(14,30); endZeit = LocalTime.of(23,0); }
        for (int i = 0; i < 5; i++) schichten.add(createSchicht(start.plusDays(i), startZeit.toString(), endZeit.toString(), typ));
        return new Arbeitsmuster(typ, 40, woche, schichten);
    }
    
    private Arbeitsmuster createCvdWochenende(int woche, LocalDate start, String typ) {
        List<Schicht> schichten = new ArrayList<>();
        LocalTime startZeit = LocalTime.of(6,0), endZeit = LocalTime.of(14,30);
        if (typ.equals("CVD_WE_SPAET")) { startZeit = LocalTime.of(14,30); endZeit = LocalTime.of(23,0); }
        schichten.add(createSchicht(start.plusDays(5), startZeit.toString(), endZeit.toString(), "CvD-Wochenende"));
        schichten.add(createSchicht(start.plusDays(6), startZeit.toString(), endZeit.toString(), "CvD-Wochenende"));
        return new Arbeitsmuster(typ, 16, woche, schichten);
    }

    private Arbeitsmuster createVollzeitWoche32h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 4; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "8h-Dienst"));
        return new Arbeitsmuster("VZ_32H", 32, woche, schichten);
    }

    private Arbeitsmuster createTeilzeitWoche30h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "6h-Dienst"));
        return new Arbeitsmuster("TZ_30H", 30, woche, schichten);
    }

    private Arbeitsmuster createTeilzeitWoche24h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 3; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "8h-Dienst"));
        return new Arbeitsmuster("TZ_24H", 24, woche, schichten);
    }
    
    private Arbeitsmuster createTeilzeitWoche20h(int woche, LocalDate start) {
        List<Schicht> schichten = List.of(
            createSchicht(start, "08:00:00", "16:30:00", "8h-Dienst"),
            createSchicht(start.plusDays(1), "08:00:00", "16:30:00", "8h-Dienst"),
            createSchicht(start.plusDays(2), "08:00:00", "12:00:00", "4h-Dienst")
        );
        return new Arbeitsmuster("TZ_20H", 20, woche, schichten);
    }

    private Arbeitsmuster createTeilzeitWoche19h(int woche, LocalDate start) {
        List<Schicht> schichten = List.of(
            createSchicht(start, "08:00:00", "16:30:00", "8h-Dienst"),
            createSchicht(start.plusDays(1), "08:00:00", "15:30:00", "7h-Dienst"),
            createSchicht(start.plusDays(2), "08:00:00", "12:00:00", "4h-Dienst")
            //createSchicht(start.plusDays(2), "08:00:00", "11:00:00", "3h-Dienst")
        );
        return new Arbeitsmuster("TZ_19H", 19, woche, schichten);
    }
    
    private Arbeitsmuster createTeilzeitWoche15h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<3; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "13:00:00", "5h-Dienst"));
        return new Arbeitsmuster("TZ_15H", 15, woche, schichten);
    }
    
    private Arbeitsmuster createTeilzeitWoche10h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<2; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "13:00:00", "5h-Dienst"));
        return new Arbeitsmuster("TZ_10H", 10, woche, schichten);
    }
    
    private Arbeitsmuster createDanieleNormalwoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "DANIELE_NORMAL"));
        // KORREKTUR: Eindeutiger Typ
        return new Arbeitsmuster("DANIELE_NORMALWOCHE", 30, woche, schichten);
    }

    private Arbeitsmuster createDanieleWochenendWoche1(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "DANIELE_WE1"));
        schichten.add(createSchicht(start.plusDays(5), "06:00:00", "14:30:00", "DANIELE_WE1"));
        schichten.add(createSchicht(start.plusDays(6), "06:00:00", "14:30:00", "DANIELE_WE1"));
        // KORREKTUR: Eindeutiger Typ
        return new Arbeitsmuster("DANIELE_WE_WOCHE_1", 46, woche, schichten); 
    }

    private Arbeitsmuster createDanieleWochenendWoche2(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=2; i<5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "DANIELE_WE2"));
        // KORREKTUR: Eindeutiger Typ
        return new Arbeitsmuster("DANIELE_WE_WOCHE_2", 18, woche, schichten); 
    }
}