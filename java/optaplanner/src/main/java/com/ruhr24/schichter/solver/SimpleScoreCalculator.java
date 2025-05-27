package com.ruhr24.schichter.solver; // ACHTUNG: Paketname anpassen, falls abweichend!

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock; // NEU: Import für SchichtBlock
import com.ruhr24.schichter.domain.SchichtPlan;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleScoreCalculator implements EasyScoreCalculator<SchichtPlan, HardSoftScore> {

    // --- Konstanten für Schicht und Planungslogik ---
    private static final double NETTO_STUNDEN_PRO_SCHICHT = 8.0; // Angenommene Netto-Arbeitszeit pro Schicht (8h Arbeit)
    private static final int MAX_MITARBEITER_PRO_SCHICHT = 5; // Allgemeine maximale Besetzung pro Schicht. Kann bei Bedarf in Schicht-Objekt verlagert werden.

    // --- Konstanten für Hard Constraints (Muss-Regeln - sehr hohe Strafen) ---
    private static final long MINDEST_RUHEZEIT_STUNDEN = 11; // § 5 ArbZG (Arbeitszeitgesetz)
    private static final int MAX_KONZEKUTIVE_ARBEITSTAGE = 7; // Max 7 Tage Arbeit am Stück erlaubt
    private static final int ERFORDERLICHE_FREIE_TAGE_NACH_STREAK = 1; // Erforderliche freie Tage nach einem Arbeitsstreak
    private static final int ERFORDERLICHE_FREIE_TAGE_NACH_7_TAGE_STREAK = 3; // Nach 7 Tagen Arbeit 3 Tage frei

    // Hard Constraints für spezifische CvD-Regeln (wenn sie absolut nicht verletzt werden dürfen)
    private static final int HARD_STRAFE_ZU_VIELE_CVDS_IN_SCHICHT = 1000; // Mehr als 1 CvD pro Schicht
    private static final int HARD_STRAFE_NICHT_CVD_IN_RANDDIENST = 1000; // Nicht-CvD in 06:00 oder 14:30 Schicht
    private static final int HARD_STRAFE_CVD_MIXED_SCHICHT_TYP_IN_WOCHE = 5000; // CvD hat gemischte Früh/Spät-Schichten in einer Woche (sehr hart)
    private static final int HARD_STRAFE_WOCHENEND_ARBEITER_KEINE_FREIEN_TAGE = 700; // Wochenendarbeiter haben Mo/Di nicht frei

    // --- Konstanten für Soft Constraints (Optimierung und Präferenzen - gewichtete Strafen/Belohnungen) ---

    // Allgemeine Belohnungen/Strafen
    private static final int SOFT_BELOHNUNG_STANDARD_WOCHEN_PATTERN = 50; // Belohnung für Mo-Fr, 08:00-16:30
    private static final int SOFT_BELOHNUNG_SPÄTSCHICHT = 25;
    private static final int SOFT_BELOHNUNG_ERFUELLTER_WUNSCH = 50;
    private static final int SOFT_STRAFE_UNBESETZTE_ZUTEILUNG = 150; // Jetzt: Unbesetzter SchichtBlock
    private static final int SOFT_STRAFE_FALSCHES_RESSORT = 15;
    private static final int SOFT_STRAFE_DAILY_COVERAGE_MINUTE = 5;
    private static final int SOFT_STRAFE_DAILY_COVERAGE_GESAMT = 75;
    private static final int SOFT_STRAFE_SOLL_STUNDEN_ABWEICHUNG_PRO_STUNDE = 10;
    private static final int SOFT_STRAFE_INKONSISTENTE_STARTZEITEN = 20; // Vermeidet ständig wechselnde Startzeiten für einen Mitarbeiter.
    private static final int SOFT_STRAFE_CVD_RANDDIENST_UEBERSCHREITUNG = 30; // CvD zu oft Randdienst im Monat
    private static final int SOFT_STRAFE_WOCHENENDDIENST_UEBERSCHREITUNG = 40; // MA zu oft WE-Dienst im Monat
    private static final int SOFT_STRAFE_WOCHENENDDIENST_ZU_NAH = 60;
    private static final int SOFT_STRAFE_WOCHENENDDIENST_UM_URLAUB = 70;
    private static final int SOFT_STRAFE_SCHICHT_AN_FEIERTAG_OHNE_MARKIERUNG = 100;
    private static final int SOFT_STRAFE_FREI_AN_WERKTAG = 20; // Strafe für freie Tage Mi, Do, Fr
    private static final int SOFT_BELOHNUNG_WOCHENEND_REDAKTEUR_PATTERN = 30; // Strafe für freie Tage Sa, So

    // Wochenend-Spezifika (stark gewichtet)
    private static final int SOFT_BELOHNUNG_WOCHENENDE_STANDARD_FREI = 500; // Sehr hohe Belohnung für freie Wochenenden für Nicht-CvDs
    private static final int SOFT_STRAFE_WOCHENENDE_NICHT_CVD_ARBEITET = 1500; // ERHÖHT: Hohe Strafe, wenn ein Nicht-CvD am WE arbeitet
    private static final int SOFT_STRAFE_WOCHENENDE_ZU_VIELE_KERN = 750; // Hohe Strafe für mehr als 2 Kernschichten am WE
    private static final int SOFT_BELOHNUNG_CVD_WE_SCHICHT_KORREKT = 400; // Gute Belohnung, wenn CvD die richtige WE-Schicht hat
    private static final int SOFT_STRAFE_CVD_WE_SCHICHT_FALSCH = 600; // Strafe, wenn CvD am WE, aber in falscher Schicht
    private static final int SOFT_STRAFE_WOCHENENDE_FEHLENDER_CVD_FRUEH = 2000; // Sehr hohe Strafe, wenn kein CvD früh am WE
    private static final int SOFT_STRAFE_WOCHENENDE_FEHLENDER_CVD_SPAET = 2000; // Sehr hohe Strafe, wenn kein CvD spät am WE

    // CvD-Streak Belohnung
    private static final int SOFT_BELOHNUNG_CVD_RANDDIENST_STREAK = 200; // Belohnung für 5-7 aufeinanderfolgende Randdienste für CvDs
    private static final int SOFT_STRAFE_CVD_KERN_SCHICHT = 800; // Hohe Strafe, wenn ein CvD eine Kernschicht hat

    // Konstante für die Wochenstunden-Schwelle, ab der zusammenhängende freie Tage gelten
    private static final double SCHWELLE_ZUSAMMENHAENGENDE_FREIE_TAGE_WOCHENSTUNDEN = 20.0;
    // Anzahl der zusammenhängenden freien Tage, wenn die Schwelle überschritten wird
    private static final int ANZAHL_ZUSAMMENHÄNGENDE_FREIE_TAGE = 2;

    // Ressort-Definitionen
    private static final String RESSORT_ONLINE_REDAKTION = "RUHR24.de";
    private static final String RESSORT_WERKSTUDENTEN = "Werkstudent:innen";

    // Block-Typen (aus SchichtBlock.blockTyp)
    private static final String BLOCK_TYP_STANDARD_REDAKTION = "STANDARD_REDAKTION";
    private static final String BLOCK_TYP_CVD_FRUEH = "CVD_FRUEH";
    private static final String BLOCK_TYP_CVD_SPAET = "CVD_SPAET";
    private static final String BLOCK_TYP_CVD_2WOCHEN = "CVD_2WOCHEN";
    private static final String BLOCK_TYP_WERKSTUDENT = "WERKSTUDENT";


    @Override
    public HardSoftScore calculateScore(SchichtPlan schichtPlan) {
        int hardScore = 0;
        int softScore = 0;

        // Maps zur Aggregation von Daten über Mitarbeiter und Schichten.
        // Diese Maps müssen jetzt aus den zugewiesenen SchichtBlöcken befüllt werden.
        Map<Schicht, Integer> schichtBesetzung = new HashMap<>(); // Zählung der Besetzung pro INDIVIDUELLER Schicht
        Map<Mitarbeiter, Double> tatsaechlichGeplanteNettoStunden = new HashMap<>();
        Map<Mitarbeiter, List<Schicht>> mitarbeiterAlleSchichten = new HashMap<>(); // Alle individuellen Schichten pro Mitarbeiter
        Map<Mitarbeiter, Set<LocalDate>> mitarbeiterArbeitstageSet = new HashMap<>();
        Map<Mitarbeiter, Map<Integer, String>> mitarbeiterCvDSchichtTypenProWoche = new HashMap<>(); // Für CvD-Wochen-Hard-Constraint
        // NEU: Map zur Verfolgung der Block-Zuweisungen pro Tag, um Überlappungen zu prüfen
        Map<Mitarbeiter, Set<LocalDate>> mitarbeiterBelegteTageDurchBlock = new HashMap<>();


        // Vorbereiten der Feiertagsliste für NRW
        Set<LocalDate> feiertageNRW = schichtPlan.getPublicHolidays() != null ? schichtPlan.getPublicHolidays() : new HashSet<>();

        // ERSTER DURCHLAUF: Zuteilungen (SchichtBlöcke) verarbeiten und Maps füllen
        for (SchichtBlock schichtBlock : schichtPlan.getSchichtBlockList()) {
            Mitarbeiter mitarbeiter = schichtBlock.getMitarbeiter(); // Der zugewiesene Mitarbeiter

            // SOFT Constraint: Unbesetzte SchichtBlöcke
            if (mitarbeiter == null) {
                hardScore -= 1000; // Oder eine noch höhere Zahl, z.B. 1_000_000_000
                System.err.println("HC: UNBESETZTER SCHICHTBLOCK: " + schichtBlock.getName() + " ist nicht besetzt. HardScore: " + hardScore);
                // Keine weiteren Prüfungen für diesen Block, da er unbesetzt ist
                continue; // Wichtig: Damit die weiteren Regeln für diesen Block nicht ausgewertet werden
            }

            // HARD Constraint: Überlappende SchichtBlöcke für denselben Mitarbeiter
            // Prüfe, ob der Mitarbeiter bereits an einem der Tage dieses Blocks zugewiesen ist
            Set<LocalDate> belegteTage = mitarbeiterBelegteTageDurchBlock.computeIfAbsent(mitarbeiter, k -> new HashSet<>());
            for (Schicht schichtInBlock : schichtBlock.getSchichtenImBlock()) {
                if (belegteTage.contains(schichtInBlock.getDatum())) {
                    hardScore -= 1000; // Sehr hohe Strafe für überlappende Blöcke
                    System.err.println("HC: ÜBERLAPPENDER SCHICHTBLOCK für " + mitarbeiter.getVorname() + " am " + schichtInBlock.getDatum() + " durch Block " + schichtBlock.getName() + ". HardScore: " + hardScore);
                }
                belegteTage.add(schichtInBlock.getDatum());
            }


            // Aggregiere Daten aus den einzelnen Schichten innerhalb des Blocks
            for (Schicht schicht : schichtBlock.getSchichtenImBlock()) {
                // Zähle die Besetzung pro individueller Schicht (für MAX_MITARBEITER_PRO_SCHICHT)
                schichtBesetzung.put(schicht, schichtBesetzung.getOrDefault(schicht, 0) + 1);

                // Aggregiere Stunden und Schichten pro Mitarbeiter
                tatsaechlichGeplanteNettoStunden.put(mitarbeiter,
                    tatsaechlichGeplanteNettoStunden.getOrDefault(mitarbeiter, 0.0) + NETTO_STUNDEN_PRO_SCHICHT);
                mitarbeiterAlleSchichten.computeIfAbsent(mitarbeiter, k -> new ArrayList<>()).add(schicht);
                mitarbeiterArbeitstageSet.computeIfAbsent(mitarbeiter, k -> new HashSet<>()).add(schicht.getDatum());

                // LOGIK FÜR CVD-WOCHEN-HARD-CONSTRAINT (gemischte Früh/Spät-Schichten in einer Woche)
                if (mitarbeiter.isCVD() && schicht.getDatum() != null && schicht.getStartZeit() != null && schicht.getEndZeit() != null) {
                    LocalTime shiftStart = schicht.getStartZeit();
                    LocalTime shiftEnd = schicht.getEndZeit();

                    String shiftType = null;
                    if (shiftStart.equals(LocalTime.of(6, 0)) && shiftEnd.equals(LocalTime.of(14, 30))) {
                        shiftType = "FRUEH";
                    } else if (shiftStart.equals(LocalTime.of(14, 30)) && shiftEnd.equals(LocalTime.of(23, 0))) {
                        shiftType = "SPAET";
                    } else if (shiftStart.equals(LocalTime.of(8, 0)) && (shiftEnd.equals(LocalTime.of(16, 30)) || shiftEnd.equals(LocalTime.of(14, 0)))) {
                        shiftType = "KERN";
                    }

                    if (shiftType != null && (shiftType.equals("FRUEH") || shiftType.equals("SPAET"))) {
                        int weekOfYear = schicht.getDatum().get(WeekFields.ISO.weekOfWeekBasedYear());
                        mitarbeiterCvDSchichtTypenProWoche
                                .computeIfAbsent(mitarbeiter, k -> new HashMap<>())
                                .merge(weekOfYear, shiftType, (oldType, newType) -> {
                                    if (!oldType.equals(newType)) {
                                        return "MIXED"; // Wenn in einer Woche gemischte CvD-Schichttypen vorkommen
                                    }
                                    return oldType;
                                });
                    }
                }
                /*
                // SOFT Constraint: Mitarbeiter sollte für das Ressort der Schicht qualifiziert sein
                // (Prüft, ob der Mitarbeiter zum Ressort der Schicht im Block passt)
                if (!mitarbeiter.getRessort().equals(schicht.getRessortBedarf())) {
                    softScore -= SOFT_STRAFE_FALSCHES_RESSORT;
                    //System.out.println("SC: Mitarbeiter " + mitarbeiter.getVorname() + " in falschem Ressort (" + schicht.getRessortBedarf() + ") für Schicht " + schicht.getDatum() + ". SoftScore: " + softScore);
                }
                */

                // SOFT Constraint: Belohnung für Spätschichten (wenn nicht CvD-Randdienst)
                if (schicht.getEndZeit().isAfter(LocalTime.of(20, 0)) && !isCvDRanddienstSchicht(schicht)) {
                    softScore += SOFT_BELOHNUNG_SPÄTSCHICHT;
                }
                /*
                // SOFT Constraint: Strafe für normale Schichten an Feiertagen (wenn nicht explizit als Feiertagsschicht markiert)
                if (feiertageNRW.contains(schicht.getDatum())) {
                    if (!schicht.isHolidayShift()) {
                        softScore -= SOFT_STRAFE_SCHICHT_AN_FEIERTAG_OHNE_MARKIERUNG;
                        //System.out.println("SC: Normale Schicht (" + schicht.getSchichtTyp() + ") an Feiertag (" + schicht.getDatum() + ") zugewiesen. SoftScore: " + softScore);
                    }
                }
                */
            }
        }


        // ZWEITER DURCHLAUF: Globale Hard Constraints prüfen (nachdem alle Zuteilungen/Blöcke verarbeitet sind)

        // HARD Constraint: CvD-Mitarbeiter müssen in einer Woche einen konsistenten Schichttyp (FRUEH oder SPAET) haben
        for (Map.Entry<Mitarbeiter, Map<Integer, String>> entry : mitarbeiterCvDSchichtTypenProWoche.entrySet()) {
            Mitarbeiter mitarbeiter = entry.getKey();
            if (mitarbeiter.isCVD()) {
                for (Map.Entry<Integer, String> weekEntry : entry.getValue().entrySet()) {
                    if (weekEntry.getValue().equals("MIXED")) {
                        hardScore -= HARD_STRAFE_CVD_MIXED_SCHICHT_TYP_IN_WOCHE;
                        //System.out.println("HC: CvD " + mitarbeiter.getVorname() + " " + mitarbeiter.getNachname() + " hat gemischte CvD-Schichten in Woche " + weekEntry.getKey() + ". HardScore: " + hardScore);
                    }
                }
            }
        }

        // HARD Constraint: Maximale Schichtbesetzung (pro individueller Schicht)
        for (Map.Entry<Schicht, Integer> entry : schichtBesetzung.entrySet()) {
            Schicht schicht = entry.getKey();
            Integer besetzung = entry.getValue();
            if (besetzung > MAX_MITARBEITER_PRO_SCHICHT) {
                hardScore -= (besetzung - MAX_MITARBEITER_PRO_SCHICHT) * 50000; // Hohe Strafe pro Überschreitung
                //System.out.println("HC: MAX BESETZUNG ÜBERSCHRITTEN für Schicht " + schicht.getDatum() + " " + schicht.getStartZeit() + ": " + besetzung + " Mitarbeiter statt max. " + MAX_MITARBEITER_PRO_SCHICHT + ". HardScore: " + hardScore);
            }
        }

        // HARD Constraint: Nur 1 CvD (Chef vom Dienst) pro Ressort pro Zeit
        // Diese Prüfung muss jetzt über die Schichten innerhalb der zugewiesenen Blöcke erfolgen
        Map<Schicht, Map<String, Integer>> cvdCountPerShiftAndRessort = new HashMap<>();
        for (SchichtBlock schichtBlock : schichtPlan.getSchichtBlockList()) {
            Mitarbeiter mitarbeiter = schichtBlock.getMitarbeiter();
            if (mitarbeiter != null && mitarbeiter.isCVD()) {
                for (Schicht schicht : schichtBlock.getSchichtenImBlock()) {
                    cvdCountPerShiftAndRessort
                        .computeIfAbsent(schicht, k -> new HashMap<>())
                        .merge(schicht.getRessortBedarf(), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<Schicht, Map<String, Integer>> entry : cvdCountPerShiftAndRessort.entrySet()) {
            Schicht schicht = entry.getKey();
            Map<String, Integer> ressortCounts = entry.getValue();
            for (Map.Entry<String, Integer> ressortEntry : ressortCounts.entrySet()) {
                String ressort = ressortEntry.getKey();
                Integer count = ressortEntry.getValue();
                if (count > 1) {
                    hardScore -= (count - 1) * HARD_STRAFE_ZU_VIELE_CVDS_IN_SCHICHT;
                    //System.out.println("HC: ZU VIELE CVDs für Ressort '" + ressort + "' in Schicht " + schicht.getDatum() + " " + schicht.getStartZeit() + ". HardScore: " + hardScore);
                }
            }
        }

        // HARD CONSTRAINT: Nicht-CVDs dürfen NICHT in 06:00 oder 14:30 Uhr Schichten besetzt werden
        // Diese Prüfung muss jetzt über die Schichten innerhalb der zugewiesenen Blöcke erfolgen
        for (SchichtBlock schichtBlock : schichtPlan.getSchichtBlockList()) {
            Mitarbeiter mitarbeiter = schichtBlock.getMitarbeiter();
            if (mitarbeiter != null && !mitarbeiter.isCVD()) {
                for (Schicht schicht : schichtBlock.getSchichtenImBlock()) {
                    if (isCvDRanddienstSchicht(schicht)) {
                        hardScore -= HARD_STRAFE_NICHT_CVD_IN_RANDDIENST;
                        //System.out.println("HC: NICHT-CVD (" + mitarbeiter.getVorname() + ") in Früh-/Spätschicht (" + schicht.getStartZeit() + ") am " + schicht.getDatum() + ". HardScore: " + hardScore);
                    }
                }
            }
        }

        // Berechne die Anzahl der Werktage im Planungszeitraum
        int anzahlWerktageImPlan = 0;
        if (schichtPlan.getVon() != null && schichtPlan.getBis() != null) {
            for (LocalDate date = schichtPlan.getVon(); !date.isAfter(schichtPlan.getBis()); date = date.plusDays(1)) {
                if (!isWeekend(date) && !feiertageNRW.contains(date)) {
                    anzahlWerktageImPlan++;
                }
            }
        }

        // SOFT Constraint: Mindestens ein Mitarbeiter pro Tag von 06:00 bis 23:00 Uhr (Abdeckung)
        // Diese Prüfung muss jetzt über die Schichten innerhalb der zugewiesenen Blöcke erfolgen
        Map<LocalDate, Set<LocalTime>> dailyCoverage = new HashMap<>();
        for (SchichtBlock schichtBlock : schichtPlan.getSchichtBlockList()) {
            Mitarbeiter mitarbeiter = schichtBlock.getMitarbeiter();
            if (mitarbeiter != null) {
                for (Schicht schicht : schichtBlock.getSchichtenImBlock()) {
                    LocalDate date = schicht.getDatum();
                    LocalTime start = schicht.getStartZeit();
                    LocalTime end = schicht.getEndZeit();
                    dailyCoverage.computeIfAbsent(date, k -> new HashSet<>());
                    LocalTime currentTime = start;
                    while (currentTime.isBefore(end)) {
                        if (!currentTime.isBefore(LocalTime.of(6, 0)) && currentTime.isBefore(LocalTime.of(23, 0))) {
                            dailyCoverage.get(date).add(currentTime);
                        }
                        currentTime = currentTime.plusMinutes(15);
                    }
                }
            }
        }
        LocalTime coverageStart = LocalTime.of(6, 0);
        LocalTime coverageEnd = LocalTime.of(23, 0);
        for (LocalDate date = schichtPlan.getVon(); !date.isAfter(schichtPlan.getBis()); date = date.plusDays(1)) {
            Set<LocalTime> coveredTimes = dailyCoverage.getOrDefault(date, Collections.emptySet());
            LocalTime currentCheckTime = coverageStart;
            boolean dayFullyCovered = true;
            while (currentCheckTime.isBefore(coverageEnd)) {
                if (!coveredTimes.contains(currentCheckTime)) {
                    softScore -= SOFT_STRAFE_DAILY_COVERAGE_MINUTE;
                    dayFullyCovered = false;
                }
                currentCheckTime = currentCheckTime.plusMinutes(15);
            }
            if (!dayFullyCovered) {
                softScore -= SOFT_STRAFE_DAILY_COVERAGE_GESAMT;
            }
        }

        // --- NEUE WOCHENEND-BESETZUNGS-REGELN ---
        // Diese Prüfung muss jetzt über die Schichten innerhalb der zugewiesenen Blöcke erfolgen
        // Zuerst aggregieren wir alle Schichten pro Tag, die tatsächlich besetzt sind
        Map<LocalDate, List<Schicht>> besetzteSchichtenProTag = new HashMap<>();
        for (SchichtBlock schichtBlock : schichtPlan.getSchichtBlockList()) {
            if (schichtBlock.getMitarbeiter() != null) { // Nur besetzte Blöcke zählen
                for (Schicht schicht : schichtBlock.getSchichtenImBlock()) {
                    besetzteSchichtenProTag.computeIfAbsent(schicht.getDatum(), k -> new ArrayList<>()).add(schicht);
                }
            }
        }

        LocalDate currentDayForWeekendCheck = schichtPlan.getVon();
        while (!currentDayForWeekendCheck.isAfter(schichtPlan.getBis())) {
            DayOfWeek dayOfWeek = currentDayForWeekendCheck.getDayOfWeek();

            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                List<Schicht> schichtenAnDiesemTag = besetzteSchichtenProTag.getOrDefault(currentDayForWeekendCheck, Collections.emptyList());

                int kernSchichtCount = 0; // 08:00-16:30
                long cvdFruehSchichtCount = 0; // 06:00-14:30
                long cvdSpaetSchichtCount = 0; // 14:30-23:00

                // Sammle die Mitarbeiter, die an diesem WE-Tag arbeiten
                Set<Mitarbeiter> mitarbeiterAmWETag = new HashSet<>();

                for (Schicht schicht : schichtenAnDiesemTag) {
                    // Finde den Mitarbeiter, der diese Schicht durch einen Block bekommen hat
                    Mitarbeiter mitarbeiterDerSchicht = null;
                    for (SchichtBlock block : schichtPlan.getSchichtBlockList()) {
                        if (block.getMitarbeiter() != null && block.getSchichtenImBlock().contains(schicht)) {
                            mitarbeiterDerSchicht = block.getMitarbeiter();
                            break;
                        }
                    }

                    if (mitarbeiterDerSchicht == null) continue; // Sollte nicht passieren, wenn alles richtig zugewiesen ist

                    mitarbeiterAmWETag.add(mitarbeiterDerSchicht);

                    // Zähle Kernschichten
                    if (schicht.getStartZeit().equals(LocalTime.of(8, 0)) && schicht.getEndZeit().equals(LocalTime.of(16, 30))) {
                        kernSchichtCount++;
                    }

                    // Zähle CvD-Schichten
                    if (mitarbeiterDerSchicht.isCVD()) {
                        if (schicht.getStartZeit().equals(LocalTime.of(6, 0)) && schicht.getEndZeit().equals(LocalTime.of(14, 30))) {
                            cvdFruehSchichtCount++;
                            softScore += SOFT_BELOHNUNG_CVD_WE_SCHICHT_KORREKT; // Belohnung für korrekte CvD-Frühschicht
                        } else if (schicht.getStartZeit().equals(LocalTime.of(14, 30)) && schicht.getEndZeit().equals(LocalTime.of(23, 0))) {
                            cvdSpaetSchichtCount++;
                            softScore += SOFT_BELOHNUNG_CVD_WE_SCHICHT_KORREKT; // Belohnung für korrekte CvD-Spätschicht
                        } else {
                            // Strafe, wenn ein CvD am Wochenende arbeitet, aber nicht in den gewünschten Randdienstschichten
                            softScore -= SOFT_STRAFE_CVD_WE_SCHICHT_FALSCH;
                            //System.out.println("SC: CvD " + mitarbeiterDerSchicht.getVorname() + " am WE in falscher Schicht: " + schicht.getStartZeit() + ". SoftScore: " + softScore);
                        }
                    }
                    // Die Strafe für Nicht-CvDs am Wochenende wird jetzt nach der Iteration über alle Mitarbeiter geprüft.
                }

                // --- Aggregierte Wochenend-Regeln pro Tag ---

                // SOFT Constraint: NUR 2 Leute in Kernschicht (08:00-16:30)
                if (kernSchichtCount > 2) {
                    softScore -= (kernSchichtCount - 2) * SOFT_STRAFE_WOCHENENDE_ZU_VIELE_KERN;
                   // System.out.println("SC: Zu viele Kernschichten am WE (" + currentDayForWeekendCheck + "): " + kernSchichtCount + ". SoftScore: " + softScore);
                }

                // SOFT Constraint: Genau ein CvD Frühschicht (06:00-14:30)
                if (cvdFruehSchichtCount == 0) {
                    softScore -= SOFT_STRAFE_WOCHENENDE_FEHLENDER_CVD_FRUEH;
                   // System.out.println("SC: Fehlender CvD Frühschicht am WE (" + currentDayForWeekendCheck + "). SoftScore: " + softScore);
                } else if (cvdFruehSchichtCount > 1) {
                    softScore -= (cvdFruehSchichtCount - 1) * SOFT_STRAFE_WOCHENENDE_ZU_VIELE_KERN; // Wiederverwendung der Strafe
                   // System.out.println("SC: Zu viele CvD Frühschichten am WE (" + currentDayForWeekendCheck + "): " + cvdFruehSchichtCount + ". SoftScore: " + softScore);
                }

                // SOFT Constraint: Genau ein CvD Spätschicht (14:30-23:00)
                if (cvdSpaetSchichtCount == 0) {
                    softScore -= SOFT_STRAFE_WOCHENENDE_FEHLENDER_CVD_SPAET;
                  //  System.out.println("SC: Fehlender CvD Spätschicht am WE (" + currentDayForWeekendCheck + "). SoftScore: " + softScore);
                } else if (cvdSpaetSchichtCount > 1) {
                    softScore -= (cvdSpaetSchichtCount - 1) * SOFT_STRAFE_WOCHENENDE_ZU_VIELE_KERN; // Wiederverwendung
                  //  System.out.println("SC: Zu viele CvD Spätschichten am WE (" + currentDayForWeekendCheck + "): " + cvdSpaetSchichtCount + ". SoftScore: " + softScore);
                }
                
                /*
                // Belohnung für freie Wochenenden für Nicht-CvDs (wenn keine Zuteilung an diesem Tag)
                for (Mitarbeiter mitarbeiter : schichtPlan.getMitarbeiterList()) {
                    // Wenn der Mitarbeiter KEIN CvD ist und KEINE Schicht an diesem WE-Tag hat
                    if (!mitarbeiter.isCVD() && !mitarbeiterAmWETag.contains(mitarbeiter)) {
                        softScore += SOFT_BELOHNUNG_WOCHENENDE_STANDARD_FREI;
                        // System.out.println("SC: MA " + mitarbeiter.getVorname() + " belohnt für freies WE (" + currentDayForWeekendCheck + "). SoftScore: " + softScore);
                    }
                    // Strafe, wenn ein NICHT-CVD Mitarbeiter am Wochenende arbeitet
                    else if (!mitarbeiter.isCVD() && mitarbeiterAmWETag.contains(mitarbeiter)) {
                        softScore -= SOFT_STRAFE_WOCHENENDE_NICHT_CVD_ARBEITET;
                        System.out.println("SC: Nicht-CvD " + mitarbeiter.getVorname() + " arbeitet am WE (" + currentDayForWeekendCheck + "). SoftScore: " + softScore);
                    }
                }
                */
            } // Ende if (Saturday || Sunday)
            currentDayForWeekendCheck = currentDayForWeekendCheck.plusDays(1);
        }

        // CUT FÜR GEMINI START
        // CUT FÜR GEMINI ENDE

        // DRITTER DURCHLAUF: Constraints, die Mitarbeiter-spezifische Daten und Historie benötigen
        for (Mitarbeiter mitarbeiter : schichtPlan.getMitarbeiterList()) {
            double zugewieseneStunden = tatsaechlichGeplanteNettoStunden.getOrDefault(mitarbeiter, 0.0);
            double sollWochenstunden = mitarbeiter.getWochenstunden();
            double sollStundenGesamtzeitraum = (sollWochenstunden / 5.0) * anzahlWerktageImPlan;

            // SOFT Constraint: Mitarbeiter soll seine Soll-Stunden erreichen (Auslastung)
            double abweichungVonSoll = Math.abs(zugewieseneStunden - sollStundenGesamtzeitraum);
            softScore -= (int) Math.round(abweichungVonSoll) * SOFT_STRAFE_SOLL_STUNDEN_ABWEICHUNG_PRO_STUNDE;

            List<Schicht> zugewieseneSchichtenDesMitarbeiters = mitarbeiterAlleSchichten.getOrDefault(mitarbeiter, Collections.emptyList());
            zugewieseneSchichtenDesMitarbeiters.sort(Comparator
                .comparing(Schicht::getDatum)
                .thenComparing(Schicht::getStartZeit));

            // --- Allgemeine Hard Constraints für ALLE Mitarbeiter ---
            // HARD Constraint: Keine überlappenden Schichten für denselben Mitarbeiter
            // (Diese Prüfung ist jetzt teilweise redundant, da wir schon überlappende Blöcke bestrafen,
            //  aber sie fängt feinere Überlappungen innerhalb eines Blocks oder bei manuellen Änderungen ab).
            for (int i = 0; i < zugewieseneSchichtenDesMitarbeiters.size(); i++) {
                Schicht currentSchicht = zugewieseneSchichtenDesMitarbeiters.get(i);
                for (int j = i + 1; j < zugewieseneSchichtenDesMitarbeiters.size(); j++) {
                    Schicht otherSchicht = zugewieseneSchichtenDesMitarbeiters.get(j);
                    if (currentSchicht.getDatum().equals(otherSchicht.getDatum())) {
                        if (currentSchicht.getStartZeit().isBefore(otherSchicht.getEndZeit()) &&
                            otherSchicht.getStartZeit().isBefore(currentSchicht.getEndZeit())) {
                            hardScore -= 50000; // Hohe Strafe
                           // System.out.println("HC: ÜBERLAPPUNG für " + mitarbeiter.getVorname() + " am " + currentSchicht.getDatum() + ": " + currentSchicht.getStartZeit() + "-" + currentSchicht.getEndZeit() + " überlappt mit " + otherSchicht.getStartZeit() + "-" + otherSchicht.getEndZeit() + ". HardScore: " + hardScore);
                        }
                    }
                }
            }

            // HARD Constraint: DEUTSCHES ARBEITSZEITGESETZ - RUHEZEIT
            for (int i = 0; i < zugewieseneSchichtenDesMitarbeiters.size() - 1; i++) {
                Schicht schicht1 = zugewieseneSchichtenDesMitarbeiters.get(i);
                Schicht schicht2 = zugewieseneSchichtenDesMitarbeiters.get(i + 1);
                LocalDateTime currentShiftEnd = LocalDateTime.of(schicht1.getDatum(), schicht1.getEndZeit());
                LocalDateTime nextShiftStart = LocalDateTime.of(schicht2.getDatum(), schicht2.getStartZeit());
                long minutesBetween = Duration.between(currentShiftEnd, nextShiftStart).toMinutes();
                if (minutesBetween < (MINDEST_RUHEZEIT_STUNDEN * 60)) {
                    hardScore -= 50000; // Hohe Strafe
                   // System.out.println("HC: RUHEZEITVERLETZUNG für " + mitarbeiter.getVorname() + " zwischen " + schicht1.getDatum() + " " + schicht1.getEndZeit() + " und " + schicht2.getDatum() + " " + schicht2.getStartZeit() + " (Dauer: " + minutesBetween + " Min). HardScore: " + hardScore);
                }
            }

            // HARD Constraint: DEUTSCHES ARBEITSZEITGESETZ - MAX KONZEKUTIVE TAGE (Generell)
            Set<LocalDate> arbeitstageDesMitarbeiters = mitarbeiterArbeitstageSet.getOrDefault(mitarbeiter, new HashSet<>());
            if (!arbeitstageDesMitarbeiters.isEmpty()) {
                LocalDate currentCheckDate = schichtPlan.getVon();
                int consecutiveWorkDays = 0;
                while (!currentCheckDate.isAfter(schichtPlan.getBis())) {
                    boolean isWorkingDay = arbeitstageDesMitarbeiters.contains(currentCheckDate);

                    if (isWorkingDay) {
                        consecutiveWorkDays++;
                        if (consecutiveWorkDays > MAX_KONZEKUTIVE_ARBEITSTAGE) {
                            hardScore -= 20000 * (consecutiveWorkDays - MAX_KONZEKUTIVE_ARBEITSTAGE);
                           // //system.out.println("HC: MAX KONZEKUTIVE TAGE ÜBERSCHRITTEN für " + mitarbeiter.getVorname() + " am " + currentCheckDate + " (Tag " + consecutiveWorkDays + "). HardScore: " + hardScore);
                        }
                    } else {
                        if (consecutiveWorkDays > 0) {
                            int requiredFreeDays = (consecutiveWorkDays >= MAX_KONZEKUTIVE_ARBEITSTAGE) ? ERFORDERLICHE_FREIE_TAGE_NACH_7_TAGE_STREAK : ERFORDERLICHE_FREIE_TAGE_NACH_STREAK;

                            int freeDaysCount = 0;
                            LocalDate tempDate = currentCheckDate;
                            while (!tempDate.isAfter(schichtPlan.getBis()) && !arbeitstageDesMitarbeiters.contains(tempDate)) {
                                freeDaysCount++;
                                if (freeDaysCount >= requiredFreeDays) {
                                    break;
                                }
                                tempDate = tempDate.plusDays(1);
                            }
                            if (freeDaysCount < requiredFreeDays) {
                                hardScore -= 30000; // Hohe Strafe
                                //system.out.println("HC: ZU WENIG FREIE TAGE nach Streak für " + mitarbeiter.getVorname() + " nach " + consecutiveWorkDays + " Arbeitstagen (nur " + freeDaysCount + " freie Tage, benötigt: " + requiredFreeDays + "). HardScore: " + hardScore);
                            }
                        }
                        consecutiveWorkDays = 0;
                    }
                    currentCheckDate = currentCheckDate.plusDays(1);
                }
            }

            // HARTE REGEL: Zusammenhängende freie Tage für MA > 20 Wochenstunden
            if (mitarbeiter.getWochenstunden() >= SCHWELLE_ZUSAMMENHAENGENDE_FREIE_TAGE_WOCHENSTUNDEN) {
                LocalDate currentWeekStart = schichtPlan.getVon();
                while (!currentWeekStart.isAfter(schichtPlan.getBis())) {
                    LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
                    if (currentWeekEnd.isAfter(schichtPlan.getBis())) {
                        currentWeekEnd = schichtPlan.getBis();
                    }

                    boolean hatInDieserWocheGearbeitet = false;
                    for (LocalDate date = currentWeekStart; !date.isAfter(currentWeekEnd); date = date.plusDays(1)) {
                        if (arbeitstageDesMitarbeiters.contains(date)) {
                            hatInDieserWocheGearbeitet = true;
                            break;
                        }
                    }

                    if (hatInDieserWocheGearbeitet) {
                        int consecutiveFreeDays = 0;
                        boolean hasRequiredFreeDays = false;
                        for (LocalDate date = currentWeekStart; !date.isAfter(currentWeekEnd); date = date.plusDays(1)) {
                            if (!arbeitstageDesMitarbeiters.contains(date)) {
                                consecutiveFreeDays++;
                                if (consecutiveFreeDays >= ANZAHL_ZUSAMMENHÄNGENDE_FREIE_TAGE) {
                                    hasRequiredFreeDays = true;
                                    break;
                                }
                            } else {
                                consecutiveFreeDays = 0;
                            }
                        }

                        if (!hasRequiredFreeDays) {
                            hardScore -= 15000; // Hohe Strafe
                            //system.out.println("HC: KEINE ZUSAMMENHÄNGENDE FREIE TAGE für " + mitarbeiter.getVorname() + " (WSt: " + sollWochenstunden + ") in Woche " + currentWeekStart + ". HardScore: " + hardScore);
                        }
                    }
                    currentWeekStart = currentWeekStart.plusWeeks(1);
                }
            }

            // SOFT Constraint: Konsistente Schichtzeiten pro Woche
            Map<Integer, Set<LocalTime>> weekStartTimes = new HashMap<>();
            LocalDate planStartDatum = schichtPlan.getVon();
            for (Schicht schicht : zugewieseneSchichtenDesMitarbeiters) {
                if (schicht.getDatum() != null && schicht.getStartZeit() != null) {
                    long daysSincePlanStart = ChronoUnit.DAYS.between(planStartDatum, schicht.getDatum());
                    int relativeWeek = (int) (daysSincePlanStart / 7);
                    weekStartTimes.computeIfAbsent(relativeWeek, k -> new HashSet<>()).add(schicht.getStartZeit());
                }
            }
            for (Set<LocalTime> startTimesInWeek : weekStartTimes.values()) {
                if (startTimesInWeek.size() > 1) {
                    softScore -= (startTimesInWeek.size() - 1) * SOFT_STRAFE_INKONSISTENTE_STARTZEITEN;
                }
            }

            // SOFT Constraint: Wünsche aus der Dienstplan-Umfrage erfüllen
            if (mitarbeiter.getWunschschichten() != null) {
                for (Schicht wunschSchicht : mitarbeiter.getWunschschichten()) {
                    if (zugewieseneSchichtenDesMitarbeiters.contains(wunschSchicht)) {
                        softScore += SOFT_BELOHNUNG_ERFUELLTER_WUNSCH;
                    }
                }
            }

            // SOFT Constraint: Kein Wochenenddienst vor oder nach Urlaub
            if (mitarbeiter.getUrlaubstageSet() != null && !mitarbeiter.getUrlaubstageSet().isEmpty()) {
                for (Schicht schicht : zugewieseneSchichtenDesMitarbeiters) {
                    LocalDate datum = schicht.getDatum();
                    if (isWeekend(datum)) {
                        for (int i = 1; i <= 7; i++) {
                            if (mitarbeiter.getUrlaubstageSet().contains(datum.plusDays(i))) {
                                softScore -= SOFT_STRAFE_WOCHENENDDIENST_UM_URLAUB;
                                break;
                            }
                        }
                        for (int i = 1; i <= 7; i++) {
                            if (mitarbeiter.getUrlaubstageSet().contains(datum.minusDays(i))) {
                                softScore -= SOFT_STRAFE_WOCHENENDDIENST_UM_URLAUB;
                                break;
                            }
                        }
                    }
                }
            }

            // SOFT Constraint: Belohnung des Standard-Arbeitsmusters (Mo-Fr, 08:00-16:30)
            // und Strafe für freie Tage Mi, Do, Fr
            LocalDate currentDayInPeriod = schichtPlan.getVon();
            while (!currentDayInPeriod.isAfter(schichtPlan.getBis())) {
                final LocalDate finalCurrentDayInPeriod = currentDayInPeriod; // <-- Diese Zeile ist korrekt!

                DayOfWeek dayOfWeek = finalCurrentDayInPeriod.getDayOfWeek(); // <-- Verwende hier die neue Variable
                boolean isWeekday = !(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY);
                boolean isHoliday = feiertageNRW.contains(finalCurrentDayInPeriod); // <-- Und hier

                // Finde die Schicht für diesen Mitarbeiter an diesem Tag (aus den zugewiesenen Blöcken)
                Schicht zugewieseneSchicht = zugewieseneSchichtenDesMitarbeiters.stream()
                    .filter(s -> s.getDatum().equals(finalCurrentDayInPeriod)) // <-- Und hier
                    .findFirst().orElse(null);

                if (isWeekday && !isHoliday) { // An Werktagen (Montag bis Freitag) und kein Feiertag
                    if (zugewieseneSchicht != null &&
                        zugewieseneSchicht.getStartZeit().equals(LocalTime.of(8, 0)) &&
                        zugewieseneSchicht.getEndZeit().equals(LocalTime.of(16, 30))) {
                        softScore += SOFT_BELOHNUNG_STANDARD_WOCHEN_PATTERN;
                        // //system.out.println("SC: MA " + mitarbeiter.getVorname() + " belohnt für Standard-Schicht am " + currentDayInPeriod + ". Score: " + softScore);
                    } else if (zugewieseneSchicht == null) { // Wenn ein Werktag frei ist (und nicht Urlaub/spez. Muster)
                        boolean isVacationDay = mitarbeiter.getUrlaubstageSet() != null && mitarbeiter.getUrlaubstageSet().contains(finalCurrentDayInPeriod); // <-- Und hier
                        if (!isVacationDay && (dayOfWeek == DayOfWeek.WEDNESDAY || dayOfWeek == DayOfWeek.THURSDAY || dayOfWeek == DayOfWeek.FRIDAY)) {
                            softScore -= SOFT_STRAFE_FREI_AN_WERKTAG;
                            // //system.out.println("SC: MA " + mitarbeiter.getVorname() + " hat am " + currentDayInPeriod + " (" + dayOfWeek + ") frei. SoftScore: " + softScore);
                        }
                    }
                }
                currentDayInPeriod = currentDayInPeriod.plusDays(1); // <-- HIER bleibt die alte Variable!
            }

            // --- TYPSPEZIFISCHE REGELN BEGINNEN HIER ---

            // 1. Regeln für CVDs
            if (mitarbeiter.isCVD()) {
                // SOFT Constraint: CvDs sollten nicht mehr als einmal im Monat Randdienst (Früh- oder Spätdienst) haben
                Map<Integer, Integer> cvdRanddienstCountPerMonth = new HashMap<>();
                for (Schicht schicht : zugewieseneSchichtenDesMitarbeiters) {
                    if (isCvDRanddienstSchicht(schicht)) {
                        int month = schicht.getDatum().getMonthValue();
                        cvdRanddienstCountPerMonth.merge(month, 1, Integer::sum);
                    }
                    // NEU: Strafe, wenn ein CvD eine Kernschicht hat
                    if (schicht.getStartZeit().equals(LocalTime.of(8, 0)) && schicht.getEndZeit().equals(LocalTime.of(16, 30))) {
                        softScore -= SOFT_STRAFE_CVD_KERN_SCHICHT;
                        //system.out.println("SC: CvD " + mitarbeiter.getVorname() + " in Kernschicht am " + schicht.getDatum() + ". SoftScore: " + softScore);
                    }
                }
                for (Map.Entry<Integer, Integer> entry : cvdRanddienstCountPerMonth.entrySet()) {
                    Integer count = entry.getValue();
                    if (count > 1) {
                        softScore -= (count - 1) * SOFT_STRAFE_CVD_RANDDIENST_UEBERSCHREITUNG;
                    }
                }

                // SOFT CONSTRAINT: Belohnung für CvD Randdienst-Streaks (5-7 Schichten)
                int currentStreak = 0;
                String currentStreakType = null; // "FRUEH" oder "SPAET"

                for (Schicht schicht : zugewieseneSchichtenDesMitarbeiters) {
                    String schichtTyp = null;
                    if (schicht.getStartZeit().equals(LocalTime.of(6, 0)) && schicht.getEndZeit().equals(LocalTime.of(14, 30))) {
                        schichtTyp = "FRUEH";
                    } else if (schicht.getStartZeit().equals(LocalTime.of(14, 30)) && schicht.getEndZeit().equals(LocalTime.of(23, 0))) {
                        schichtTyp = "SPAET";
                    }

                    if (schichtTyp != null) {
                        if (schichtTyp.equals(currentStreakType)) {
                            currentStreak++;
                        } else {
                            if (currentStreak >= 5 && currentStreak <= 7) {
                                softScore += SOFT_BELOHNUNG_CVD_RANDDIENST_STREAK;
                                //system.out.println("SC: CvD " + mitarbeiter.getVorname() + " belohnt für " + currentStreak + " " + currentStreakType + " Streak. SoftScore: " + softScore);
                            }
                            currentStreak = 1;
                            currentStreakType = schichtTyp;
                        }
                    } else {
                        if (currentStreak >= 5 && currentStreak <= 7) {
                            softScore += SOFT_BELOHNUNG_CVD_RANDDIENST_STREAK;
                            //system.out.println("SC: CvD " + mitarbeiter.getVorname() + " belohnt für " + currentStreak + " " + currentStreakType + " Streak. SoftScore: " + softScore);
                        }
                        currentStreak = 0;
                        currentStreakType = null;
                    }
                }
                if (currentStreak >= 5 && currentStreak <= 7) {
                    softScore += SOFT_BELOHNUNG_CVD_RANDDIENST_STREAK;
                    //system.out.println("SC: CvD " + mitarbeiter.getVorname() + " belohnt für " + currentStreak + " " + currentStreakType + " Streak (Ende der Liste). SoftScore: " + softScore);
                }

                // HARD REGEL: Zusätzliche Regel für Wochenendarbeiter (7 Tage Arbeit, dann Mo/Di frei)
                LocalDate currentWeekStartDate = schichtPlan.getVon();
                while (!currentWeekStartDate.isAfter(schichtPlan.getBis())) {
                    LocalDate mondayOfCurrentWeek = currentWeekStartDate;
                    while (mondayOfCurrentWeek.getDayOfWeek() != DayOfWeek.MONDAY && !mondayOfCurrentWeek.isAfter(schichtPlan.getBis())) {
                        mondayOfCurrentWeek = mondayOfCurrentWeek.plusDays(1);
                    }
                    if (mondayOfCurrentWeek.isAfter(schichtPlan.getBis())) break;

                    LocalDate saturdayOfCurrentWeek = mondayOfCurrentWeek.plusDays(5);
                    LocalDate sundayOfCurrentWeek = mondayOfCurrentWeek.plusDays(6);
                    LocalDate mondayOfNextWeek = mondayOfCurrentWeek.plusDays(7);
                    LocalDate tuesdayOfNextWeek = mondayOfCurrentWeek.plusDays(8);

                    boolean workedOnSaturday = arbeitstageDesMitarbeiters.contains(saturdayOfCurrentWeek);
                    boolean workedOnSunday = arbeitstageDesMitarbeiters.contains(sundayOfCurrentWeek);

                    if (workedOnSaturday && workedOnSunday) {
                        if (arbeitstageDesMitarbeiters.contains(mondayOfNextWeek)) {
                            hardScore -= HARD_STRAFE_WOCHENEND_ARBEITER_KEINE_FREIEN_TAGE;
                            //system.out.println("HC: Wochenend-Arbeiter " + mitarbeiter.getVorname() + " muss Montag (" + mondayOfNextWeek + ") frei haben. HardScore: " + hardScore);
                        }
                        if (arbeitstageDesMitarbeiters.contains(tuesdayOfNextWeek)) {
                            hardScore -= HARD_STRAFE_WOCHENEND_ARBEITER_KEINE_FREIEN_TAGE;
                            //system.out.println("HC: Wochenend-Arbeiter " + mitarbeiter.getVorname() + " muss Dienstag (" + tuesdayOfNextWeek + ") frei haben. HardScore: " + hardScore);
                        }
                    }
                    currentWeekStartDate = currentWeekStartDate.plusWeeks(1);
                }
            }

            // 2. Regeln für "normale" Online-Redakteure (Ressort RUHR24.de, aber KEIN CvD)
            else if (mitarbeiter.getRessort().equals(RESSORT_ONLINE_REDAKTION) && !mitarbeiter.isCVD()) {
                // SOFT CONSTRAINT: Belohnung für das "Wochenend-Redakteur"-Muster
                LocalDate currentWeekStart = schichtPlan.getVon();
                while (!currentWeekStart.isAfter(schichtPlan.getBis())) {
                    LocalDate mondayOfCurrentWeek = currentWeekStart;
                    while (mondayOfCurrentWeek.getDayOfWeek() != DayOfWeek.MONDAY && !mondayOfCurrentWeek.isAfter(schichtPlan.getBis())) {
                        mondayOfCurrentWeek = mondayOfCurrentWeek.plusDays(1);
                    }
                    if (mondayOfCurrentWeek.isAfter(schichtPlan.getBis())) break;

                    LocalDate saturdayOfCurrentWeek = mondayOfCurrentWeek.plusDays(5);
                    LocalDate sundayOfCurrentWeek = mondayOfCurrentWeek.plusDays(6);

                    LocalDate mondayOfNextWeek = mondayOfCurrentWeek.plusDays(7);
                    LocalDate tuesdayOfNextWeek = mondayOfCurrentWeek.plusDays(8);
                    LocalDate wednesdayOfNextWeek = mondayOfCurrentWeek.plusDays(9);
                    LocalDate thursdayOfNextWeek = mondayOfCurrentWeek.plusDays(10);
                    LocalDate fridayOfNextWeek = mondayOfCurrentWeek.plusDays(11);


                    boolean workedOnSaturday = arbeitstageDesMitarbeiters.contains(saturdayOfCurrentWeek);
                    boolean workedOnSunday = arbeitstageDesMitarbeiters.contains(sundayOfCurrentWeek);

                    if (workedOnSaturday && workedOnSunday) {
                        boolean workedMonNextWeek = arbeitstageDesMitarbeiters.contains(mondayOfNextWeek);
                        boolean workedTueNextWeek = arbeitstageDesMitarbeiters.contains(tuesdayOfNextWeek);
                        boolean workedWedNextWeek = arbeitstageDesMitarbeiters.contains(wednesdayOfNextWeek);
                        boolean workedThuNextWeek = arbeitstageDesMitarbeiters.contains(thursdayOfNextWeek);
                        boolean workedFriNextWeek = arbeitstageDesMitarbeiters.contains(fridayOfNextWeek);

                        boolean patternMatched = (!workedMonNextWeek && !workedTueNextWeek &&
                                                 workedWedNextWeek && workedThuNextWeek && workedFriNextWeek);

                        if (patternMatched) {
                            softScore += SOFT_BELOHNUNG_WOCHENEND_REDAKTEUR_PATTERN;
                            //system.out.println("SC: Wochenend-Redakteur-Muster erfüllt für " + mitarbeiter.getVorname() + " beginnend Woche " + mondayOfCurrentWeek + ". SoftScore: " + softScore);
                        } else {
                            softScore -= (SOFT_BELOHNUNG_WOCHENEND_REDAKTEUR_PATTERN / 2); // Leichte Strafe, wenn Muster nicht perfekt ist
                            //system.out.println("SC: Wochenend-Redakteur-Muster NICHT erfüllt für " + mitarbeiter.getVorname() + " beginnend Woche " + mondayOfCurrentWeek + ". SoftScore: " + softScore);
                        }
                    }
                    currentWeekStart = currentWeekStart.plusWeeks(1);
                }
            }

            // 3. Regeln für Werkstudenten (Beispiel: Annahme, dass sie ein spezifisches Ressort haben)
            else if (mitarbeiter.getRessort().equals(RESSORT_WERKSTUDENTEN) || mitarbeiter.getStellenbezeichnung().toLowerCase().contains("werkstudent")) {
                // Werkstudenten sollen keine Randdienste (Früh/Spät) haben
                for (Schicht schicht : zugewieseneSchichtenDesMitarbeiters) {
                    if (isCvDRanddienstSchicht(schicht)) { // Nutze die gleiche Methode, da es die gleichen Zeiten sind
                        softScore -= 300; // Hohe Strafe für Werkstudenten in Randdiensten
                        System.out.println("SC: Werkstudent " + mitarbeiter.getVorname() + " in Randdienst am " + schicht.getDatum() + ". SoftScore: " + softScore);
                    }
                    if (isWeekend(schicht.getDatum())) {
                        softScore -= 500; // Hohe Strafe für Werkstudenten am Wochenende
                        System.out.println("SC: Werkstudent " + mitarbeiter.getVorname() + " am Wochenende am " + schicht.getDatum() + ". SoftScore: " + softScore);
                    }
                }
            }
            // --- TYPSPEZIFISCHE REGELN ENDEN HIER ---
        }
        return HardSoftScore.of(hardScore, softScore);
    }


    // --- Hilfsmethoden ---
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private boolean isCvDRanddienstSchicht(Schicht schicht) {
        if (schicht == null || schicht.getStartZeit() == null || schicht.getEndZeit() == null) {
            return false;
        }
        LocalTime start = schicht.getStartZeit();
        LocalTime end = schicht.getEndZeit();
        return (start.equals(LocalTime.of(6, 0)) && end.equals(LocalTime.of(14, 30))) ||
               (start.equals(LocalTime.of(14, 30)) && end.equals(LocalTime.of(23, 0)));
    }
}
