
package com.ruhr24.schichter.generator;

import com.ruhr24.schichter.domain.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

public class MusterGenerator {
    public List<Arbeitsmuster> generate(LocalDate vonDatum, LocalDate bisDatum, List<Mitarbeiter> mitarbeiterList, 
                                        List<Wunsch> alleWuensche, List<Abwesenheit> alleAbwesenheiten) {
        
        if (alleAbwesenheiten == null) {
            alleAbwesenheiten = Collections.emptyList();
        }

        List<Arbeitsmuster> finalMusterPool = new ArrayList<>();
        Map<String, List<Wunsch>> wuenscheProMitarbeiter = alleWuensche.stream()
                .collect(Collectors.groupingBy(Wunsch::getMitarbeiterId));
        int anzahlWochenendDiensteLetzteWoche = 0;

        Map<String, String> spezialMitarbeiterMap = new HashMap<>();
        for (Mitarbeiter m : mitarbeiterList) {
            if (m.hasQualification("DANIELE_SONDERDIENST")) spezialMitarbeiterMap.put("DANIELE_SONDERDIENST", m.getId());
            if (m.hasQualification("LIBE_SONDERDIENST")) spezialMitarbeiterMap.put("LIBE_SONDERDIENST", m.getId());
            if (m.hasQualification("KEITER_SONDERDIENST")) spezialMitarbeiterMap.put("KEITER_SONDERDIENST", m.getId());
        }

        LocalDate startWoche = vonDatum.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!startWoche.isAfter(bisDatum)) {
            final LocalDate aktuelleWoche = startWoche;

            int wocheDesJahres = aktuelleWoche.get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear());
            final LocalDate wochenEnde = aktuelleWoche.plusDays(6);

            Map<String, Long> abgedeckterBedarf = new HashMap<>();
            abgedeckterBedarf.put("CVD_FRUEH", 0L);
            abgedeckterBedarf.put("CVD_SPAET", 0L);
            abgedeckterBedarf.put("CVD_WE_FRUEH", 0L);
            abgedeckterBedarf.put("CVD_WE_SPAET", 0L);
            abgedeckterBedarf.put("REDAKTION_WE_WOCHE_1", 0L);

            Set<String> mitarbeiterDieseWocheVerplant = new HashSet<>();
            Set<String> mitarbeiterMitWochenendeDieseWoche = new HashSet<>();

            Set<String> abwesendeMitarbeiterIds = alleAbwesenheiten.stream()
                .filter(abw -> !(abw.bis().isBefore(aktuelleWoche) || abw.von().isAfter(wochenEnde)))
                .map(Abwesenheit::mitarbeiterId)
                .collect(Collectors.toSet());

            List<Mitarbeiter> verfuegbareMitarbeiter = mitarbeiterList.stream()
                .filter(m -> !abwesendeMitarbeiterIds.contains(m.getId()))
                .collect(Collectors.toList());

            handleWunschZuweisungen(
                wocheDesJahres, aktuelleWoche, verfuegbareMitarbeiter,
                wuenscheProMitarbeiter, finalMusterPool, mitarbeiterDieseWocheVerplant,
                mitarbeiterMitWochenendeDieseWoche, abgedeckterBedarf
            );

            List<Arbeitsmuster> unbesetzteJobsDieserWoche = new ArrayList<>();

            for (int i = 0; i < anzahlWochenendDiensteLetzteWoche; i++) {
                if (i % 2 == 0) unbesetzteJobsDieserWoche.add(createCvdAusgleichWoche(wocheDesJahres, aktuelleWoche));
                else unbesetzteJobsDieserWoche.add(createRedaktionAusgleichWoche(wocheDesJahres, aktuelleWoche));
            }

            int anzahlCvdWeFrueh = (int)(1 - abgedeckterBedarf.get("CVD_WE_FRUEH"));
            int anzahlCvdWeSpaet = (int)(1 - abgedeckterBedarf.get("CVD_WE_SPAET"));
            int anzahlRedWe = (int)(2 - abgedeckterBedarf.get("REDAKTION_WE_WOCHE_1"));
            for (int i = 0; i < (1 - abgedeckterBedarf.get("CVD_FRUEH")); i++) unbesetzteJobsDieserWoche.add(createCvdWoche(wocheDesJahres, aktuelleWoche, "CVD_FRUEH"));
            for (int i = 0; i < (1 - abgedeckterBedarf.get("CVD_SPAET")); i++) unbesetzteJobsDieserWoche.add(createCvdWoche(wocheDesJahres, aktuelleWoche, "CVD_SPAET"));
            for (int i = 0; i < anzahlCvdWeFrueh; i++) unbesetzteJobsDieserWoche.add(createCvdWochenende(wocheDesJahres, aktuelleWoche, "CVD_WE_FRUEH"));
            for (int i = 0; i < anzahlCvdWeSpaet; i++) unbesetzteJobsDieserWoche.add(createCvdWochenende(wocheDesJahres, aktuelleWoche, "CVD_WE_SPAET"));
            for (int i = 0; i < anzahlRedWe; i++) unbesetzteJobsDieserWoche.add(createRedaktionWochenendWoche(wocheDesJahres, aktuelleWoche));

            if (!mitarbeiterDieseWocheVerplant.contains(spezialMitarbeiterMap.get("DANIELE_SONDERDIENST"))) unbesetzteJobsDieserWoche.add(createDanieleNormalwoche(wocheDesJahres, aktuelleWoche));
            if (!mitarbeiterDieseWocheVerplant.contains(spezialMitarbeiterMap.get("LIBE_SONDERDIENST"))) unbesetzteJobsDieserWoche.add(createLisaBenderWoche(wocheDesJahres, aktuelleWoche));
            if (!mitarbeiterDieseWocheVerplant.contains(spezialMitarbeiterMap.get("KEITER_SONDERDIENST"))) unbesetzteJobsDieserWoche.add(createKeiterNormalwoche(wocheDesJahres, aktuelleWoche));

            List<Mitarbeiter> mitarbeiterOhneWunsch = verfuegbareMitarbeiter.stream()
                .filter(m -> !mitarbeiterDieseWocheVerplant.contains(m.getId()))
                .collect(Collectors.toList());

            for (Mitarbeiter mitarbeiter : mitarbeiterOhneWunsch) {
                unbesetzteJobsDieserWoche.add(createStandardMusterFuerMitarbeiter(mitarbeiter, wocheDesJahres, aktuelleWoche));
            }

            finalMusterPool.addAll(unbesetzteJobsDieserWoche);

            long anzahlWochenendDiensteDieseWoche = mitarbeiterMitWochenendeDieseWoche.size() + anzahlCvdWeFrueh + anzahlCvdWeSpaet + anzahlRedWe;
            anzahlWochenendDiensteLetzteWoche = (int) anzahlWochenendDiensteDieseWoche;

            startWoche = startWoche.plusWeeks(1);
        }

        System.out.println("Gesamtanzahl erstellter Muster (Aufgaben für den Solver): " + finalMusterPool.size());
        return finalMusterPool;
    }

    private void handleWunschZuweisungen(int wocheDesJahres, LocalDate aktuelleWoche, List<Mitarbeiter> verfuegbareMitarbeiter,
                                         Map<String, List<Wunsch>> wuenscheProMitarbeiter, List<Arbeitsmuster> finalMusterPool,
                                         Set<String> mitarbeiterDieseWocheVerplant, Set<String> mitarbeiterMitWochenendeDieseWoche,
                                         Map<String, Long> abgedeckterBedarf) {

        for (Mitarbeiter mitarbeiter : verfuegbareMitarbeiter) {
            final LocalDate wochenAnfang = aktuelleWoche;
            final LocalDate wochenEnde = aktuelleWoche.plusDays(6);

            Optional<Wunsch> preflightOpt = wuenscheProMitarbeiter.getOrDefault(mitarbeiter.getId(), Collections.emptyList())
                .stream()
                .filter(w -> w.getTyp() == WunschTyp.PREFLIGHT && w.getDatum() != null &&
                             !w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isBefore(wochenAnfang) &&
                             !w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(wochenEnde))
                .findFirst();

            if (preflightOpt.isPresent()) {
                Arbeitsmuster muster = createMusterFromPreflightDetails(mitarbeiter, wocheDesJahres, preflightOpt.get().getDetails());
                muster.setPinned(true); 
                finalMusterPool.add(muster);
                mitarbeiterDieseWocheVerplant.add(mitarbeiter.getId());
                
                if (preflightOpt.get().getDetails().keySet().stream().map(LocalDate::parse)
                        .anyMatch(d -> d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)) {
                    mitarbeiterMitWochenendeDieseWoche.add(mitarbeiter.getId());
                    abgedeckterBedarf.merge("REDAKTION_WE_WOCHE_1", 1L, Long::sum);
                }
                continue;
            }

            List<Wunsch> wochenWuensche = wuenscheProMitarbeiter.getOrDefault(mitarbeiter.getId(), Collections.emptyList())
                .stream()
                .filter(w -> w.getTyp() == WunschTyp.MUSS &&
                             w.getDatum() != null &&
                             !w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isBefore(wochenAnfang) &&
                             !w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(wochenEnde))
                .collect(Collectors.toList());
            
            if (!wochenWuensche.isEmpty()) {
                Arbeitsmuster wunschMuster = createWunschMuster(mitarbeiter, wochenWuensche, wocheDesJahres);
                wunschMuster.setPinned(true);
                finalMusterPool.add(wunschMuster);
                mitarbeiterDieseWocheVerplant.add(mitarbeiter.getId());
                
                if (istWochenendWunsch(wochenWuensche)) {
                    mitarbeiterMitWochenendeDieseWoche.add(mitarbeiter.getId());
                    if (abgedeckterBedarf.get("REDAKTION_WE_WOCHE_1") < 2) {
                        abgedeckterBedarf.merge("REDAKTION_WE_WOCHE_1", 1L, Long::sum);
                    }
                    if (mitarbeiter.hasQualification("CVD")) {
                        if (istFruehWunsch(wochenWuensche)) abgedeckterBedarf.merge("CVD_WE_FRUEH", 1L, Long::sum);
                        else abgedeckterBedarf.merge("CVD_WE_SPAET", 1L, Long::sum);
                    }
                } else {
                    if (mitarbeiter.hasQualification("CVD")) {
                        if (istFruehWunsch(wochenWuensche)) abgedeckterBedarf.merge("CVD_FRUEH", 1L, Long::sum);
                        else abgedeckterBedarf.merge("CVD_SPAET", 1L, Long::sum);
                    }
                }
            }
        }
    }
    
    private Arbeitsmuster createStandardMusterFuerMitarbeiter(Mitarbeiter mitarbeiter, int woche, LocalDate start) {
        switch (mitarbeiter.getWochenstunden()) {
            case 40: return createRedaktionWoche(woche, start);
            case 30: return createTeilzeitWoche30h(woche, start);
            case 20: return createTeilzeitWoche20h(woche, start);
            case 19: return createTeilzeitWoche19h(woche, start);
            case 15: return createTeilzeitWoche15h(woche, start);
            case 10: return createTeilzeitWoche10h(woche, start);
            default: 
                return createRedaktionWoche(woche, start); 
        }
    }

    private Arbeitsmuster createWunschMuster(Mitarbeiter mitarbeiter, List<Wunsch> wuensche, int woche) {
        List<Schicht> mussSchichten = wuensche.stream()
            .filter(w -> w.getTyp() == WunschTyp.MUSS && w.getVon() != null)
            .map(w -> createSchicht(w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate(), w.getVon().toString(), w.getBis().toString(), "WUNSCH_MUSS"))
            .collect(Collectors.toList());
            
        Arbeitsmuster muster = new Arbeitsmuster("WUNSCH_" + mitarbeiter.getId(), 0, woche, mussSchichten);
        muster.setMitarbeiter(mitarbeiter);
        return muster;
    }
    
    private Arbeitsmuster createMusterFromPreflightDetails(Mitarbeiter mitarbeiter, int woche, Map<String, String> details) {
        List<Schicht> schichten = new ArrayList<>();
        if (details == null) {
            return new Arbeitsmuster("PREFLIGHT_LEER", 0, woche, schichten);
        }
        
        for (Map.Entry<String, String> entry : details.entrySet()) {
            LocalDate datum = LocalDate.parse(entry.getKey());
            String status = entry.getValue();

            if ("Muss".equalsIgnoreCase(status) || "on".equalsIgnoreCase(status)) {
                schichten.add(createSchicht(datum, "08:00:00", "16:30:00", "PREFLIGHT_ON"));
            } else if (!"Frei".equalsIgnoreCase(status) && !"off".equalsIgnoreCase(status)) {
                // Optional: Handle specific shift types
            }
        }
        
        int stunden = schichten.size() * 8; 
        Arbeitsmuster muster = new Arbeitsmuster("PREFLIGHT_" + mitarbeiter.getId(), stunden, woche, schichten);
        muster.setMitarbeiter(mitarbeiter);
        return muster;
    }

    private Schicht createSchicht(LocalDate datum, String start, String ende, String typ) {
        return new Schicht(UUID.randomUUID(), datum, LocalTime.parse(start), LocalTime.parse(ende), "Bedarf", 1, typ, false);
    }
    
    private boolean istWochenendWunsch(List<Wunsch> wuensche) {
        return wuensche.stream().anyMatch(w -> {
            DayOfWeek tag = w.getDatum().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().getDayOfWeek();
            return tag == DayOfWeek.SATURDAY || tag == DayOfWeek.SUNDAY;
        });
    }

    private boolean istFruehWunsch(List<Wunsch> wuensche) {
        return wuensche.stream().anyMatch(w -> w.getVon() != null && w.getVon().getHour() < 12);
    }

    //Redaktion Schichtmuster
    private Arbeitsmuster createRedaktionWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KERNDIENST_REDAKTION"));
        }
        return new Arbeitsmuster("REDAKTION_40H", 40, woche, schichten);
    }
    private Arbeitsmuster createRedaktionWochenendWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KERNDIENST_REDAKTION"));
        }
        schichten.add(createSchicht(start.plusDays(5), "08:00:00", "16:30:00", "REDAKTION_WOCHENENDE"));
        schichten.add(createSchicht(start.plusDays(6), "08:00:00", "16:30:00", "REDAKTION_WOCHENENDE"));
        return new Arbeitsmuster("REDAKTION_WE_WOCHE", 56, woche, schichten);
    }
    private Arbeitsmuster createRedaktionAusgleichWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 2; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "REDAKTION_AUSGLEICH"));
        }
        return new Arbeitsmuster("REDAKTION_AUSGLEICH_WOCHE", 24, woche, schichten);
    }

    //CvD Schichtmuster
    private Arbeitsmuster createCvdWoche(int woche, LocalDate start, String typ) {
        List<Schicht> schichten = new ArrayList<>();
        LocalTime startZeit = LocalTime.of(8,0), endZeit = LocalTime.of(16,30);
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
        for (int i = 2; i < 5; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "CVD_AUSGLEICH"));
        }
        return new Arbeitsmuster("CVD_AUSGLEICH_WOCHE", 24, woche, schichten);
    }

    //Teilzeit Schichtmuster
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

    //Spezielle Schichtmuster
    private Arbeitsmuster createDanieleNormalwoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "14:00:00", "DANIELE_NORMAL"));
        return new Arbeitsmuster("DANIELE_NORMALWOCHE", 30, woche, schichten);
    }
    private Arbeitsmuster createKeiterNormalwoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i=0; i<5; i++) schichten.add(createSchicht(start.plusDays(i), "08:00:00", "16:30:00", "KEITER_NORMAL"));
        return new Arbeitsmuster("KEITER_SONDERDIENST", 40, woche, schichten);
    }
    private Arbeitsmuster createLisaBenderWoche(int woche, LocalDate start) {
        List<Schicht> schichten = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            schichten.add(createSchicht(start.plusDays(i), "08:00:00", "13:00:00", "5h-Dienst"));
        }
        return new Arbeitsmuster("LIBE_SONDERDIENST", 20, woche, schichten);
    }
}
