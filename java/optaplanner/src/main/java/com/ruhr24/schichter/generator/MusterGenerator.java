package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.Arbeitsmuster;
import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.Wunsch;
import com.ruhr24.schichter.domain.WunschTyp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

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
    private static final int REDAKTION_WOCHEN_PRO_WOCHE = 14;
    //private static final int REDAKTION_WE_BLOECKE_PRO_MONAT = 5;
    private static final int CVD_KERN_WOCHEN_PRO_WOCHE = 9;
    //private static final int CVD_FRUEH_WOCHEN_PRO_WOCHE = 1;
    //private static final int CVD_SPAET_WOCHEN_PRO_WOCHE = 1;
    //private static final int CVD_WE_FRUEH_PRO_WOCHE = 1;
    //private static final int CVD_WE_SPAET_PRO_WOCHE = 1;
    private static final int TZ_30H_WOCHEN_PRO_WOCHE = 1;
    private static final int TZ_24H_WOCHEN_PRO_WOCHE = 1;
    private static final int TZ_20H_WOCHEN_PRO_WOCHE = 2;
    private static final int TZ_19H_WOCHEN_PRO_WOCHE = 6;
    private static final int TZ_15H_WOCHEN_PRO_WOCHE = 2;
    private static final int TZ_10H_WOCHEN_PRO_WOCHE = 1;


    public List<Arbeitsmuster> generate(LocalDate von, LocalDate bis, List<Mitarbeiter> mitarbeiterList, List<Wunsch> alleWuensche) {
        List<Arbeitsmuster> musterPool = new ArrayList<>();


        Map<String, List<Wunsch>> wuenscheProMitarbeiter = alleWuensche.stream()
                .collect(Collectors.groupingBy(Wunsch::getMitarbeiterId));

        Map<String, Set<Integer>> verplanteMitarbeiterWochen = new HashMap<>();

        for (Mitarbeiter mitarbeiter : mitarbeiterList) {
            LocalDate wochePruefen = von.with(DayOfWeek.MONDAY);
            while (!wochePruefen.isAfter(bis)) {
                final LocalDate aktuelleWoche = wochePruefen;
                int wocheNummer = aktuelleWoche.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());

                List<Wunsch> wochenWuensche = (wuenscheProMitarbeiter.get(mitarbeiter.getId()) != null)
                    ? wuenscheProMitarbeiter.get(mitarbeiter.getId()).stream()
                        .filter(w -> {
                            LocalDate wunschDatum = w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                            return !wunschDatum.isBefore(aktuelleWoche) && wunschDatum.isBefore(aktuelleWoche.plusWeeks(1));
                        })
                        .collect(Collectors.toList())
                    : Collections.emptyList();

                if (wochenWuensche.stream().anyMatch(w -> w.getTyp() == WunschTyp.MUSS)) {
                    musterPool.addAll(createWunschMuster(mitarbeiter, wochenWuensche, wocheNummer));
                    verplanteMitarbeiterWochen.computeIfAbsent(mitarbeiter.getId(), k -> new HashSet<>()).add(wocheNummer);
                }
                wochePruefen = wochePruefen.plusWeeks(1);
            }
        }

        LocalDate startWoche = von.with(DayOfWeek.MONDAY);
        while (!startWoche.isAfter(bis)) {
            int wocheDesJahres = startWoche.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
            LocalDate naechsteWoche = startWoche.plusWeeks(1);

            //CVD Mo - Fr
            musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_FRUEH"));
            musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_SPAET"));

            //Wochenend-Dienste CVD
            musterPool.add(createCvdWochenende(wocheDesJahres, startWoche, "CVD_WE_FRUEH"));
            musterPool.add(createCvdWochenende(wocheDesJahres, startWoche, "CVD_WE_SPAET"));
            musterPool.add(createRedaktionWochenendWoche(wocheDesJahres, startWoche));
            musterPool.add(createRedaktionWochenendWoche(wocheDesJahres, startWoche));

            //Abfrage für erste Woche, da gibt es ja keinen ausgleich
            if (startWoche.isEqual(von)) {
                musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_KERN"));
                musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_KERN"));
                musterPool.add(createRedaktionWoche(wocheDesJahres, startWoche));
                musterPool.add(createRedaktionWoche(wocheDesJahres, startWoche));
            }

            if (!naechsteWoche.isAfter(bis)) {
                int wocheDanach = naechsteWoche.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
                musterPool.add(createCvdAusgleichWoche(wocheDanach, naechsteWoche));
                musterPool.add(createCvdAusgleichWoche(wocheDanach, naechsteWoche));
                musterPool.add(createRedaktionAusgleichWoche(wocheDanach, naechsteWoche));
                musterPool.add(createRedaktionAusgleichWoche(wocheDanach, naechsteWoche));
            }
        

            //Standardmuster
            for (int i = 0; i < ADMIN_WOCHEN_PRO_WOCHE; i++) {
                musterPool.add(createAdminWoche(wocheDesJahres, startWoche));
            }
            for (int i = 0; i < CVD_KERN_WOCHEN_PRO_WOCHE; i++) {
                musterPool.add(createCvdWoche(wocheDesJahres, startWoche, "CVD_KERN"));
            }
            for (int i = 0; i < REDAKTION_WOCHEN_PRO_WOCHE; i++) {
                musterPool.add(createRedaktionWoche(wocheDesJahres, startWoche));
            }
            for (int i = 0; i < TZ_19H_WOCHEN_PRO_WOCHE; i++) {
                musterPool.add(createTeilzeitWoche19h(wocheDesJahres, startWoche));
            }

            //Sonderfälle
            if (!verplanteMitarbeiterWochen.getOrDefault("005", Collections.emptySet()).contains(wocheDesJahres)) {
                musterPool.add(createDanieleNormalwoche(wocheDesJahres, startWoche));
            }
            if (!verplanteMitarbeiterWochen.getOrDefault("013", Collections.emptySet()).contains(wocheDesJahres)) {
                musterPool.add(createLisaBenderWoche(wocheDesJahres, startWoche));
            }
            musterPool.add(createTeilzeitWoche30h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche20h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche20h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche15h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche15h(wocheDesJahres, startWoche));
            musterPool.add(createTeilzeitWoche10h(wocheDesJahres, startWoche));
            startWoche = startWoche.plusWeeks(1);

        }

        System.out.println("--- Erstellte Arbeitsmuster ---");
        for (Arbeitsmuster muster : musterPool) {
            System.out.println(
                String.format("Woche: %d, Typ: %-30s, Stunden: %d, Schichten: %d",
                    muster.getWocheImJahr(),
                    "'" + muster.getMusterTyp() + "'",
                    muster.getWochenstunden(),
                    muster.getSchichten().size()
                )
            );
        }

        System.out.println("===============================");
        System.out.println("Gesamtanzahl erstellter Muster: " + musterPool.size());
        return musterPool;
    }

    // --- HILFSMETHODEN ---

    private List<Arbeitsmuster> createWunschMuster(Mitarbeiter mitarbeiter, List<Wunsch> wuensche, int woche) {
        List<Schicht> mussSchichten = wuensche.stream()
            .filter(w -> w.getTyp() == WunschTyp.MUSS && w.getVon() != null)
            .map(w -> createSchicht(w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(), w.getVon().toString(), w.getBis().toString(), "WUNSCH_MUSS"))
            .collect(Collectors.toList());
            
        if (!mussSchichten.isEmpty()) {
            Arbeitsmuster muster = new Arbeitsmuster(
                "WUNSCH_MUSS_" + mitarbeiter.getNachname().toUpperCase(), 
                0, // Stunden werden hier nicht berechnet, da sie fix sind
                woche, 
                mussSchichten
            );
            // Das ist der entscheidende Teil: Das Muster wird fest dem Mitarbeiter zugewiesen.
            muster.setMitarbeiter(mitarbeiter); 
            return List.of(muster);
        }
        return Collections.emptyList();
    }
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

        for (int i = 0; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "CVD_KERN_WE"));
        }

        LocalTime startZeit = LocalTime.of(6,0), endZeit = LocalTime.of(14,30);
        if (typ.equals("CVD_WE_SPAET")) { startZeit = LocalTime.of(14,30); endZeit = LocalTime.of(23,0); }
        schichten.add(createSchicht(start.plusDays(5), startZeit.toString(), endZeit.toString(), "CvD-Wochenende"));
        schichten.add(createSchicht(start.plusDays(6), startZeit.toString(), endZeit.toString(), "CvD-Wochenende"));
        return new Arbeitsmuster(typ, 56, woche, schichten);
    }

    private Arbeitsmuster createCvdAusgleichWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        // Mi, Do, Fr normale 8h-Schicht
        for (int i = 2; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "CVD_AUSGLEICH"));
        }
        return new Arbeitsmuster("CVD_AUSGLEICH_WOCHE", 24, woche, schichten);
    }

    private Arbeitsmuster createTeilzeitWoche30h(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "6h-Dienst"));
        return new Arbeitsmuster("TZ_30H", 30, woche, schichten);
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
}