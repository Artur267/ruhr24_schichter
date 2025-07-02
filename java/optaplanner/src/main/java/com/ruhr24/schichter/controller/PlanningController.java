/*

package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
//import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.SchichtPlan;
//import com.ruhr24.schichter.generator.SchichtBlockGenerator; // Importiere den Generator
import com.ruhr24.schichter.generator.SchichtGenerator; // Importiere den Generator
import com.ruhr24.schichter.dto.PlanungsanfrageDto;

import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
//import java.time.LocalTime;
//import java.time.Duration;
//import java.util.Collections;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
//import java.util.HashMap;
import java.util.LinkedHashMap;
import java.time.format.DateTimeFormatter;
//import java.util.Arrays;
//import java.util.Comparator;

@Controller
@RequestMapping("/api")
public class PlanningController {

    private final SolverManager<SchichtPlan, UUID> solverManager;
    private final Map<UUID, SchichtPlan> solvedPlans = new ConcurrentHashMap<>();
    private final Map<UUID, SolverJob<SchichtPlan, UUID>> activeSolverJobs = new ConcurrentHashMap<>();
    private final SchichtGenerator schichtGenerator; // Instanz des Generators

    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");
    private static final String JAVA_OUTPUT_DIR_RELATIVE = "results";
    private static final String FINAL_CSV_FILENAME = "output.csv";
    private static final String FULL_JAVA_CSV_PATH = CURRENT_WORKING_DIR + File.separator + JAVA_OUTPUT_DIR_RELATIVE + File.separator + FINAL_CSV_FILENAME;

    @Autowired
    public PlanningController(SolverManager<SchichtPlan, UUID> solverManager) {
        this.solverManager = solverManager;
        this.schichtGenerator = new SchichtGenerator(); // Generator hier instanziieren

        System.out.println("[JAVA BACKEND] Aktuelles Arbeitsverzeichnis (user.dir): " + CURRENT_WORKING_DIR);
        System.out.println("[JAVA BACKEND] CSV wird gespeichert unter (vollständiger Pfad): " + FULL_JAVA_CSV_PATH);

        File javaOutputDirectory = new File(CURRENT_WORKING_DIR, JAVA_OUTPUT_DIR_RELATIVE);
        if (!javaOutputDirectory.exists()) {
            javaOutputDirectory.mkdirs();
            System.out.println("[JAVA BACKEND] OptaPlanner Modul-Ausgabeverzeichnis erstellt: " + javaOutputDirectory.getAbsolutePath());
        }
    }

    @PostMapping("/planen")
    @ResponseBody
    public ResponseEntity<String> planeSchichten(@RequestBody PlanungsanfrageDto anfrageDto,
                                                 @RequestParam(defaultValue = "10") int dailyCoreServiceSlots) { // dailyCoreServiceSlots als RequestParam
        UUID problemId = UUID.randomUUID();
        System.out.println("[JAVA BACKEND] 🧠 Starte Planung mit ID: " + problemId);

        // dailyCoreServiceSlots wird jetzt direkt an bauePlanungsproblem übergeben
        SchichtPlan initialProblem = bauePlanungsproblem(anfrageDto, problemId, dailyCoreServiceSlots);

        Function<UUID, SchichtPlan> problemSupplier = currentProblemId -> {
            System.out.println("[JAVA BACKEND] Baue Planungsproblem für ID: " + currentProblemId);
            return initialProblem;
        };

        Consumer<SchichtPlan> bestSolutionConsumer = bestSolution -> {
            // Optional: Logge hier die besten Zwischenergebnisse, wenn nötig
        };

        Consumer<SchichtPlan> finalSolutionConsumer = finalSolution -> {
            System.out.println("[JAVA BACKEND] ✅ Finale Lösung empfangen für ID: " + problemId + " - Consumer gestartet.");
            //gibLösungsLoggingAus(finalSolution); // Standard-Logging
            solvedPlans.put(problemId, finalSolution); // Speichere die Lösung

            try {
                schreibeLösungAlsCsv(finalSolution, FULL_JAVA_CSV_PATH);
            } catch (IOException e) {
                System.err.println("[JAVA BACKEND] ❌ Fehler beim Schreiben der CSV-Datei für ID " + problemId + ": " + e.getMessage());
                e.printStackTrace();
            }

            activeSolverJobs.remove(problemId);
            System.out.println("[JAVA BACKEND] ✅ Finale Lösung empfangen für ID: " + problemId + " - Consumer beendet.");
            // gibLösungsLoggingAus(finalSolution); // Doppelt, kann entfernt werden
        };

        BiConsumer<UUID, Throwable> exceptionConsumer = (uuid, throwable) -> {
            System.err.println("\n\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("!!!!!!!!!!!!!! OPTAPLANNER FEHLER GEFUNDEN !!!!!!!!!!!!!!");
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
            System.err.println("[JAVA BACKEND] ❌ Fehler für ID: " + uuid);
            
            // Dieser Befehl druckt den eigentlichen, detaillierten Fehler
            throwable.printStackTrace(); 
            
            System.err.println("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.err.println("!!!!!!!!!!!!!! ENDE OPTAPLANNER FEHLER !!!!!!!!!!!!!!!");
            System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n\n");
            
            activeSolverJobs.remove(uuid);
        };

        SolverJob<SchichtPlan, UUID> solverJob = solverManager.solveAndListen(
            problemId,
            problemSupplier,
            bestSolutionConsumer,
            finalSolutionConsumer,
            exceptionConsumer
        );

        activeSolverJobs.put(problemId, solverJob);

        return ResponseEntity.accepted().body("Planung mit ID " + problemId + " gestartet.");
    }

    @GetMapping("/planen/{problemId}")
    @ResponseBody
    public ResponseEntity<SchichtPlan> getPlanungsErgebnis(@PathVariable UUID problemId) {
        SchichtPlan solution = solvedPlans.get(problemId);
        if (solution != null) {
            System.out.println("[JAVA BACKEND] Lösung für ID " + problemId + " gefunden.");
            return ResponseEntity.ok(solution);
        }

        SolverJob<SchichtPlan, UUID> solverJob = activeSolverJobs.get(problemId);
        if (solverJob != null) {
            System.out.println("[JAVA BACKEND] Job " + problemId + " läuft noch.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } else {
            System.out.println("[JAVA BACKEND] Job " + problemId + " nicht gefunden oder nicht mehr aktiv. Möglicherweise Fehler aufgetreten oder Lösung nicht gespeichert.");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/mitarbeiterkalender")
    public String showMitarbeiterKalender(@RequestParam(required = false) UUID planId, Model model) {
        List<Map<String, Object>> mitarbeiterDaten = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        if (planId != null) {
            SchichtPlan solution = solvedPlans.get(planId);
            if (solution != null) {
                System.out.println("[JAVA BACKEND] Mitarbeiterkalender-Anzeige für Plan ID: " + planId);
                mitarbeiterDaten = konvertiereLösungFuerFrontend(solution);

                List<LocalDate> allDatesInPlan = new ArrayList<>();
                for (LocalDate date = solution.getVon(); !date.isAfter(solution.getBis()); date = date.plusDays(1)) {
                    allDatesInPlan.add(date);
                }
                dates = allDatesInPlan.stream()
                                .map(date -> date.format(DateTimeFormatter.ofPattern("dd.MM.")))
                                .collect(Collectors.toList());
            } else {
                System.out.println("[JAVA BACKEND] Lösung für Plan ID " + planId + " nicht gefunden.");
            }
        } else {
            System.out.println("[JAVA BACKEND] Keine Plan ID für Mitarbeiterkalender-Anzeige angegeben. Zeige leere Tabelle.");
        }

        model.addAttribute("mitarbeiterDaten", mitarbeiterDaten);
        model.addAttribute("dates", dates);
        model.addAttribute("planId", planId);
        return "mitarbeiterKalender";
    }

    private void schreibeLösungAlsCsv(SchichtPlan solution, String filePath) throws IOException {
    File outputFile = new File(filePath);

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // 1. Header erstellen (Dein Code, bleibt unverändert)
            List<String> headerColumns = new ArrayList<>(List.of("NutzerID", "Nachname", "Vorname", "E-Mail", "Stellenbezeichnung", "Ressort", "CVD", "Qualifikationen", "Teams", "Notizen", "Wochenstunden", "MonatsSumme", "Delta"));
            List<LocalDate> allDatesInPlan = solution.getVon().datesUntil(solution.getBis().plusDays(1)).collect(Collectors.toList());
            List<String> datesFormatted = allDatesInPlan.stream().map(date -> date.format(DateTimeFormatter.ofPattern("dd.MM."))).collect(Collectors.toList());
            List<String> fullHeader = new ArrayList<>(headerColumns);
            for (String date : datesFormatted) {
                fullHeader.add(date + " Von");
                fullHeader.add(date + " Bis");
            }
            writer.println(String.join(";", fullHeader));

            // 2. Werktage berechnen (Dein Code, bleibt unverändert)
            int anzahlWerktageImPlan = 0;
            for (LocalDate date = solution.getVon(); !date.isAfter(solution.getBis()); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && !solution.getPublicHolidays().contains(date)) {
                    anzahlWerktageImPlan++;
                }
            }

            // 3. KORRIGIERTE DATENSAMMLUNG: Wir gruppieren die zugewiesenen Schichten nach Mitarbeiter
            Map<Mitarbeiter, List<Schicht>> schichtenProMitarbeiter = solution.getSchichtList().stream()
                    .filter(s -> s.getMitarbeiter() != null)
                    .collect(Collectors.groupingBy(Schicht::getMitarbeiter));

            // 4. Schleife über ALLE Mitarbeiter, um sicherzustellen, dass jeder eine Zeile bekommt
            for (Mitarbeiter m : solution.getMitarbeiterList()) {
                List<String> row = new ArrayList<>();
                // Stammdaten hinzufügen (Dein Code, bleibt unverändert)
                row.add(m.getNutzerID());
                row.add(m.getNachname());
                row.add(m.getVorname());
                row.add(m.getEmail() != null ? m.getEmail() : "");
                row.add(m.getStellenbezeichnung() != null ? m.getStellenbezeichnung() : "");
                row.add(m.getRessort());
                row.add(String.valueOf(m.isCVD()));
                row.add(m.getRollenUndQualifikationen() != null ? String.join(", ", m.getRollenUndQualifikationen()) : "");
                row.add(m.getTeamsUndZugehoerigkeiten() != null ? String.join(", ", m.getTeamsUndZugehoerigkeiten()) : "");
                row.add(m.getNotizen());
                row.add(String.valueOf(m.getWochenstunden()));
                
                // Stundenberechnung basierend auf der neuen Map
                List<Schicht> zugewieseneSchichten = schichtenProMitarbeiter.getOrDefault(m, Collections.emptyList());
                double totalHours = zugewieseneSchichten.stream().mapToLong(Schicht::getArbeitszeitInMinuten).sum() / 60.0;
                double targetHours = (m.getWochenstunden() / 5.0) * anzahlWerktageImPlan;
                double delta = totalHours - targetHours;

                row.add(String.format(Locale.GERMANY, "%.2f", totalHours));
                row.add(String.format(Locale.GERMANY, "%.2f", delta));

                // Schichtzeiten für jeden Tag in die CSV-Zeile schreiben
                Map<LocalDate, Schicht> tagesSchichtMap = zugewieseneSchichten.stream()
                        .collect(Collectors.toMap(Schicht::getDatum, Function.identity(), (s1, s2) -> s1)); // Bei Doppelschichten nur die erste nehmen

                for (LocalDate date : allDatesInPlan) {
                    Schicht schichtAnDiesemTag = tagesSchichtMap.get(date);
                    if (schichtAnDiesemTag != null) {
                        row.add(schichtAnDiesemTag.getStartZeit().format(DateTimeFormatter.ofPattern("HH:mm")));
                        row.add(schichtAnDiesemTag.getEndZeit().format(DateTimeFormatter.ofPattern("HH:mm")));
                    } else {
                        row.add("");
                        row.add("");
                    }
                }
                writer.println(String.join(";", row));
            }
        }
    }

    private SchichtPlan bauePlanungsproblem(PlanungsanfrageDto anfrageDto, UUID problemId, int dailyCoreServiceSlots) { // dailyCoreServiceSlots als Parameter hinzugefügt
        SchichtPlan plan = new SchichtPlan();
        plan.setId(problemId);

        if (anfrageDto.getVon() == null || anfrageDto.getBis() == null) {
            System.err.println("[JAVA BACKEND] FEHLER: Planungszeitraum fehlt im DTO! Kann kein Problem bauen.");
            throw new IllegalArgumentException("Planungszeitraum (von/bis) darf nicht null sein.");
        }

        LocalDate start = anfrageDto.getVon();
        LocalDate end = anfrageDto.getBis();
        List<Mitarbeiter> mitarbeiterList = anfrageDto.getMitarbeiterList();
        Set<LocalDate> publicHolidaysNRW = getPublicHolidaysNRW(start.getYear());

        plan.setPublicHolidays(publicHolidaysNRW);
        plan.setVon(start);
        plan.setBis(end);
        plan.setMitarbeiterList(mitarbeiterList);
        plan.setRessort(anfrageDto.getRessort());

        List<Schicht> generatedSchichten = schichtGenerator.generate(start, end);
        plan.setSchichtList(generatedSchichten);

        if (mitarbeiterList != null) {
            System.out.println("[JAVA BACKEND] Mitarbeiter erhalten: " + mitarbeiterList.size());
            mitarbeiterList.forEach(m -> System.out.println("[JAVA BACKEND]  - " + m.getVorname() + " " + m.getNachname() +
                    ", Ressort: " + m.getRessort() + // Ressort ist jetzt String
                    ", Wochenstunden: " + m.getWochenstunden() + // Wochenstunden ist jetzt int
                    ", CVD: " + m.isCVD() +
                    ", E-Mail: " + (m.getEmail() != null ? m.getEmail() : "N/A") +
                    ", Stellenbezeichnung: " + (m.getStellenbezeichnung() != null ? m.getStellenbezeichnung() : "N/A") +
                    ", Qualifikationen: " + (m.getRollenUndQualifikationen() != null ? String.join(", ", m.getRollenUndQualifikationen()) : "N/A") +
                    ", Teams: " + (m.getTeamsUndZugehoerigkeiten() != null ? String.join(", ", m.getTeamsUndZugehoerigkeiten()) : "N/A")
            ));
        } else {
            System.out.println("[JAVA BACKEND] KEINE MITARBEITER IM DTO ERHALTEN. Fortfahren mit leerer Mitarbeiterliste.");
        }

        System.out.println("[JAVA BACKEND] Generierte Schichten: " + generatedSchichten.size());

        System.out.println("[DEBUG] Anzahl der SchichtBlöcke im finalen SchichtPlan (vor Übergabe an Solver): " + plan.getSchichtList().size());
        if (!plan.getSchichtList().isEmpty()) {
            Schicht firstBlock = plan.getSchichtList().get(0);
            generatedSchichten.forEach(s -> System.out.println("[JAVA BACKEND]  - " + s.getSchichtTyp() + " am " + s.getDatum()));
            System.out.println("[JAVA BACKEND]  - Mitarbeiter zugewiesen: " + (firstBlock.getMitarbeiter() != null ? firstBlock.getMitarbeiter().getNachname() : "null"));
        } else {
            System.out.println("[DEBUG] SchichtList im Plan ist leer.");
        }


        return plan;
    }

    private List<Map<String, Object>> konvertiereLösungFuerFrontend(SchichtPlan solution) {
        Map<String, Map<String, Object>> mitarbeiterDatenMap = new LinkedHashMap<>();

        // Initialisiere die Map mit allen Mitarbeitern
        for (Mitarbeiter m : solution.getMitarbeiterList()) {
            Map<String, Object> mitarbeiterData = new LinkedHashMap<>();
            mitarbeiterData.put("id", m.getId());
            mitarbeiterData.put("name", m.getVorname() + " " + m.getNachname());
            mitarbeiterData.put("geplanteSchichten", new LinkedHashMap<String, List<Map<String, String>>>());
            mitarbeiterDatenMap.put(m.getId(), mitarbeiterData);
        }
        
        // Iteriere über die zugewiesenen Schichten und füge sie dem jeweiligen Mitarbeiter hinzu
        if (solution.getSchichtList() != null) {
            for (Schicht schicht : solution.getSchichtList()) {
                if (schicht.getMitarbeiter() != null) {
                    Map<String, Object> mitarbeiterData = mitarbeiterDatenMap.get(schicht.getMitarbeiter().getId());
                    if (mitarbeiterData != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, List<Map<String, String>>> geplanteSchichten = (Map<String, List<Map<String, String>>>) mitarbeiterData.get("geplanteSchichten");
                        
                        String datumKey = schicht.getDatum().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        List<Map<String, String>> schichtenAmTag = geplanteSchichten.computeIfAbsent(datumKey, k -> new ArrayList<>());

                        Map<String, String> schichtDetails = new LinkedHashMap<>();
                        schichtDetails.put("start", schicht.getStartZeit().format(DateTimeFormatter.ISO_LOCAL_TIME));
                        schichtDetails.put("end", schicht.getEndZeit().format(DateTimeFormatter.ISO_LOCAL_TIME));
                        schichtDetails.put("typ", schicht.getSchichtTyp());
                        schichtDetails.put("ressort", schicht.getRessortBedarf());
                        schichtenAmTag.add(schichtDetails);
                    }
                }
            }
        }

        return new ArrayList<>(mitarbeiterDatenMap.values());
    }

    private Set<LocalDate> getPublicHolidaysNRW(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(LocalDate.of(year, 1, 1)); // Neujahr
        holidays.add(LocalDate.of(year, 5, 1)); // Tag der Arbeit
        holidays.add(LocalDate.of(year, 10, 3)); // Tag der Deutschen Einheit
        holidays.add(LocalDate.of(year, 11, 1)); // Allerheiligen (NRW)
        holidays.add(LocalDate.of(year, 12, 25)); // 1. Weihnachtstag
        holidays.add(LocalDate.of(year, 12, 26)); // 2. Weihnachtstag

        // Dynamische Berechnung der Osterfeiertage (Karfreitag, Ostermontag, Christi Himmelfahrt, Pfingstmontag, Fronleichnam)
        // Quelle: Osterformel nach Gauß
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int n = (h + l - 7 * m + 114) % 31;
        int month = (h + l - 7 * m + 114) / 31;
        int day = n + 1;

        LocalDate easterSunday = LocalDate.of(year, month, day);

        holidays.add(easterSunday.minusDays(2)); // Karfreitag
        holidays.add(easterSunday.plusDays(1));  // Ostermontag
        holidays.add(easterSunday.plusDays(39)); // Christi Himmelfahrt
        holidays.add(easterSunday.plusDays(50)); // Pfingstmontag
        holidays.add(easterSunday.plusDays(60)); // Fronleichnam

        return holidays;
    }
}
     */