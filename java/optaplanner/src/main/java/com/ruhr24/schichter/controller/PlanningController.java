package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.generator.SchichtBlockGenerator; // Importiere den Generator
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
import java.time.LocalTime;
import java.time.Duration;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

@Controller
@RequestMapping("/api")
public class PlanningController {

    private final SolverManager<SchichtPlan, UUID> solverManager;
    private final Map<UUID, SchichtPlan> solvedPlans = new ConcurrentHashMap<>();
    private final Map<UUID, SolverJob<SchichtPlan, UUID>> activeSolverJobs = new ConcurrentHashMap<>();
    private final SchichtBlockGenerator schichtBlockGenerator; // Instanz des Generators

    private static final String CURRENT_WORKING_DIR = System.getProperty("user.dir");
    private static final String JAVA_OUTPUT_DIR_RELATIVE = "results";
    private static final String FINAL_CSV_FILENAME = "output.csv";
    private static final String FULL_JAVA_CSV_PATH = CURRENT_WORKING_DIR + File.separator + JAVA_OUTPUT_DIR_RELATIVE + File.separator + FINAL_CSV_FILENAME;

    @Autowired
    public PlanningController(SolverManager<SchichtPlan, UUID> solverManager) {
        this.solverManager = solverManager;
        this.schichtBlockGenerator = new SchichtBlockGenerator(); // Generator hier instanziieren

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
            System.err.println("[JAVA BACKEND] ❌ Fehler während der Planung für ID: " + uuid + ": " + throwable.getMessage());
            throwable.printStackTrace();
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
            List<String> headerColumns = new ArrayList<>();
            headerColumns.add("NutzerID");
            headerColumns.add("Nachname");
            headerColumns.add("Vorname");
            headerColumns.add("E-Mail");
            headerColumns.add("Stellenbezeichnung");
            headerColumns.add("Ressort");
            headerColumns.add("CVD");
            headerColumns.add("Qualifikationen");
            headerColumns.add("Teams");
            headerColumns.add("Notizen");
            headerColumns.add("Wochenstunden");
            headerColumns.add("MonatsSumme");
            headerColumns.add("Delta");

            List<LocalDate> allDatesInPlan = new ArrayList<>();
            for (LocalDate date = solution.getVon(); !date.isAfter(solution.getBis()); date = date.plusDays(1)) {
                allDatesInPlan.add(date);
            }
            List<String> datesFormatted = allDatesInPlan.stream()
                                    .map(date -> date.format(DateTimeFormatter.ofPattern("dd.MM.")))
                                    .collect(Collectors.toList());

            List<String> fullHeader = new ArrayList<>(headerColumns);
            for (String date : datesFormatted) {
                fullHeader.add(date + " Von");
                fullHeader.add(date + " Bis");
            }
            writer.println(String.join(";", fullHeader));

            // Berechnung der Werktage für Delta
            int anzahlWerktageImPlan = 0;
            if (solution.getVon() != null && solution.getBis() != null) {
                for (LocalDate date = solution.getVon(); !date.isAfter(solution.getBis()); date = date.plusDays(1)) {
                    // Berücksichtige hier auch Feiertage, falls sie nicht als Werktage zählen sollen
                    if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && !solution.getPublicHolidays().contains(date)) {
                        anzahlWerktageImPlan++;
                    }
                }
            }

            // NEU: Map zur Aggregation der tatsächlich geplanten Stunden pro Mitarbeiter
            Map<Mitarbeiter, Double> tatsaechlichGeplanteStundenProMitarbeiter = new HashMap<>();

            // NEU: Daten aus SchichtBlöcken extrahieren und Stunden aggregieren
            Map<String, Map<String, Object>> mitarbeiterDatenMap = new LinkedHashMap<>();
            for (Mitarbeiter m : solution.getMitarbeiterList()) {
                // Initialisiere tatsaechlichGeplanteStundenProMitarbeiter für jeden Mitarbeiter
                tatsaechlichGeplanteStundenProMitarbeiter.put(m, 0.0);

                Map<String, Object> mitarbeiterData = new LinkedHashMap<>();
                mitarbeiterData.put("NutzerID", m.getNutzerID());
                mitarbeiterData.put("Nachname", m.getNachname());
                mitarbeiterData.put("Vorname", m.getVorname());
                mitarbeiterData.put("E-Mail", m.getEmail() != null ? m.getEmail() : "");
                mitarbeiterData.put("Stellenbezeichnung", m.getStellenbezeichnung() != null ? m.getStellenbezeichnung() : "");
                mitarbeiterData.put("Ressort", m.getRessort());
                mitarbeiterData.put("CVD", m.isCVD());
                mitarbeiterData.put("Qualifikationen", m.getRollenUndQualifikationen() != null ? String.join(", ", m.getRollenUndQualifikationen()) : "");
                mitarbeiterData.put("Teams", m.getTeamsUndZugehoerigkeiten() != null ? String.join(", ", m.getTeamsUndZugehoerigkeiten()) : "");
                mitarbeiterData.put("Notizen", m.getNotizen());
                mitarbeiterData.put("Wochenstunden", m.getWochenstunden());

                // MonatsSumme und Delta werden später nach der Aggregation aller Schichten aktualisiert
                mitarbeiterData.put("MonatsSumme", "0.00"); // Temporär
                mitarbeiterData.put("Delta", "0.00");     // Temporär

                mitarbeiterData.put("Arbeitszeiten", new LinkedHashMap<String, Map<String, String>>());
                mitarbeiterDatenMap.put(m.getNutzerID(), mitarbeiterData);
            }

            // Iteriere über die zugewiesenen SchichtBlöcke, um Arbeitszeiten und Stunden zu aggregieren
            if (solution.getSchichtBlockList() != null) {
                for (SchichtBlock block : solution.getSchichtBlockList()) {
                    Mitarbeiter mitarbeiter = block.getMitarbeiter();
                    if (mitarbeiter != null && block.getSchichtenImBlock() != null) {
                        Map<String, Object> mitarbeiterData = mitarbeiterDatenMap.get(mitarbeiter.getNutzerID());
                        if (mitarbeiterData != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Map<String, String>> arbeitszeiten =
                                    (Map<String, Map<String, String>>) mitarbeiterData.get("Arbeitszeiten");

                            double blockNettoStunden = 0.0; // Stunden für diesen Schichtblock

                            for (Schicht schicht : block.getSchichtenImBlock()) {
                                String datumKey = schicht.getDatum().format(DateTimeFormatter.ofPattern("dd.MM."));
                                Map<String, String> zeiten = new HashMap<>();
                                zeiten.put("Von", schicht.getStartZeit().format(DateTimeFormatter.ofPattern("HH:mm")));
                                zeiten.put("Bis", schicht.getEndZeit().format(DateTimeFormatter.ofPattern("HH:mm")));
                                zeiten.put("Typ", schicht.getSchichtTyp() != null ? schicht.getSchichtTyp() : "");
                                arbeitszeiten.put(datumKey, zeiten); // Überschreibt, falls mehrere Schichten am selben Tag im Block sind

                                // NEU: Stunden für die einzelne Schicht berechnen
                                // Annahme: 30 Minuten Pause für Schichten länger als 6 Stunden
                                // Passe die Pausenlogik bei Bedarf an
                                Duration duration = Duration.between(schicht.getStartZeit(), schicht.getEndZeit());
                                double nettoStundenSchicht = duration.toMinutes() / 60.0;

                                // Beispiel: 30 Minuten Pause bei Schichten ab 6 Stunden
                                if (nettoStundenSchicht >= 6.0) {
                                    nettoStundenSchicht -= 0.5; // 30 Minuten = 0.5 Stunden
                                }
                                blockNettoStunden += nettoStundenSchicht;
                            }
                            // Addiere die berechneten Stunden des Blocks zu den Gesamtstunden des Mitarbeiters
                            tatsaechlichGeplanteStundenProMitarbeiter.merge(mitarbeiter, blockNettoStunden, Double::sum);
                        }
                    }
                }
            }
            
            // Jetzt, da alle Stunden aggregiert sind, aktualisiere MonatsSumme und Delta
            for (Map<String, Object> mitarbeiterRow : mitarbeiterDatenMap.values()) {
                String nutzerId = (String) mitarbeiterRow.get("NutzerID");
                Mitarbeiter m = solution.getMitarbeiterList().stream()
                                    .filter(x -> x.getNutzerID().equals(nutzerId))
                                    .findFirst().orElse(null);

                if (m != null) {
                    double monatsSumme = tatsaechlichGeplanteStundenProMitarbeiter.getOrDefault(m, 0.0);
                    mitarbeiterRow.put("MonatsSumme", String.format("%.2f", monatsSumme));

                    double sollWochenstunden = m.getWochenstunden();
                    double sollStundenGesamtzeitraum = (sollWochenstunden / 5.0) * anzahlWerktageImPlan;
                    double delta = monatsSumme - sollStundenGesamtzeitraum;
                    mitarbeiterRow.put("Delta", String.format("%.2f", delta));
                }
            }


            for (Map<String, Object> mitarbeiterRow : mitarbeiterDatenMap.values()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(mitarbeiterRow.get("NutzerID")));
                row.add(String.valueOf(mitarbeiterRow.get("Nachname")));
                row.add(String.valueOf(mitarbeiterRow.get("Vorname")));
                row.add(String.valueOf(mitarbeiterRow.get("E-Mail")));
                row.add(String.valueOf(mitarbeiterRow.get("Stellenbezeichnung")));
                row.add(String.valueOf(mitarbeiterRow.get("Ressort")));
                row.add(String.valueOf(mitarbeiterRow.get("CVD")));
                row.add(String.valueOf(mitarbeiterRow.get("Qualifikationen")));
                row.add(String.valueOf(mitarbeiterRow.get("Teams")));
                row.add(String.valueOf(mitarbeiterRow.get("Notizen")));
                row.add(String.valueOf(mitarbeiterRow.get("Wochenstunden")));
                row.add(String.valueOf(mitarbeiterRow.get("MonatsSumme")));
                row.add(String.valueOf(mitarbeiterRow.get("Delta")));

                @SuppressWarnings("unchecked")
                Map<String, Map<String, String>> arbeitszeiten = (Map<String, Map<String, String>>) mitarbeiterRow.get("Arbeitszeiten");
                for (LocalDate date : allDatesInPlan) {
                    String dateFormatted = date.format(DateTimeFormatter.ofPattern("dd.MM."));
                    Map<String, String> zeiten = arbeitszeiten.get(dateFormatted);
                    row.add(zeiten != null ? zeiten.get("Von") : "");
                    row.add(zeiten != null ? zeiten.get("Bis") : "");
                }
                writer.println(String.join(";", row));
            }
            System.out.println("[JAVA BACKEND] ✅ CSV-Datei erfolgreich gespeichert unter: " + outputFile.getAbsolutePath());
        }
    }


    /**
     * Baut das initiale Planungsproblem für OptaPlanner auf, basierend auf SchichtBlöcken.
     * @param anfrageDto Das DTO mit den Planungsdaten.
     * @param problemId Die ID des Planungsproblems.
     * @param dailyCoreServiceSlots Anzahl der Kerndienst-Slots pro Wochentag (Mo-Fr).
     * @return Das initialisierte SchichtPlan-Objekt.
     */
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
        plan.setRessort(anfrageDto.getRessort()); // Setze das Ressort aus dem DTO

        // HIER WIRD DER SCHICHTBLOCKGENERATOR AUFGERUFEN UND DIE BLÖCKE HINZUGEFÜGT
        List<SchichtBlock> generatedSchichtBlocks = schichtBlockGenerator.generateSchichtBlocks(start, end);
        // WICHTIG: Die generierten SchichtBlöcke dem Plan hinzufügen!
        plan.setSchichtBlockList(generatedSchichtBlocks);


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
            mitarbeiterList = Collections.emptyList();
        }

        System.out.println("[JAVA BACKEND] Generierte Schichtblöcke: " + generatedSchichtBlocks.size());
        generatedSchichtBlocks.forEach(sb -> System.out.println("[JAVA BACKEND]  - " + sb.getName() + " (" + sb.getBlockTyp() + ")"));

        // NEUE DEBUG-AUSGABEN HIER
        System.out.println("[DEBUG] Anzahl der SchichtBlöcke im finalen SchichtPlan (vor Übergabe an Solver): " + plan.getSchichtBlockList().size());
        if (!plan.getSchichtBlockList().isEmpty()) {
            SchichtBlock firstBlock = plan.getSchichtBlockList().get(0);
            System.out.println("[DEBUG] Erster SchichtBlock im Plan: " + firstBlock.getName() +
                               ", Mitarbeiter zugewiesen: " + (firstBlock.getMitarbeiter() != null ? firstBlock.getMitarbeiter().getNachname() : "null"));
        } else {
            System.out.println("[DEBUG] SchichtBlockList im Plan ist leer.");
        }


        return plan;
    }

    // Hilfsmethode zur Ausgabe der Lösung im Log
    private void gibLösungsLoggingAus(SchichtPlan solution) {
        System.out.println("----------------------------------------------------");
        System.out.println("OPTIMIERUNG ABGESCHLOSSEN!");
        System.out.println("Finaler Score: " + solution.getScore()); // Diese Zeile ist wichtig!
        System.out.println("----------------------------------------------------");

        if (solution.getSchichtBlockList() != null) {
            Map<Mitarbeiter, List<SchichtBlock>> zugewieseneBloeckeProMitarbeiter = solution.getSchichtBlockList().stream()
                    .filter(block -> block.getMitarbeiter() != null)
                    .collect(Collectors.groupingBy(SchichtBlock::getMitarbeiter));

            // Sortierung nach Nachname und Vorname für bessere Lesbarkeit
            zugewieseneBloeckeProMitarbeiter.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Mitarbeiter::getNachname)
                                                      .thenComparing(Mitarbeiter::getVorname)))
                    .forEach(entry -> {
                        Mitarbeiter mitarbeiter = entry.getKey();
                        List<SchichtBlock> bloecke = entry.getValue();
                        System.out.println("[JAVA BACKEND] Mitarbeiter " + mitarbeiter.getVorname() + " " + mitarbeiter.getNachname() + " hat folgende Blöcke:");
                        bloecke.stream()
                                .sorted(Comparator.comparing(SchichtBlock::getBlockStartDatum))
                                .forEach(block -> {
                                    System.out.println("[JAVA BACKEND]   SchichtBlock: '" + block.getName() + "' (" + block.getBlockStartDatum() + " - " + block.getBlockEndDatum() + ") zugewiesen an: " +
                                            (block.getMitarbeiter() != null ? block.getMitarbeiter().getVorname() + " " + block.getMitarbeiter().getNachname() : "UNZUGWIESEN") +
                                            " (Typ: " + block.getBlockTyp() + ")");
                                    block.getSchichtenImBlock().forEach(schicht ->
                                            System.out.println("[JAVA BACKEND]     - Schicht: " + schicht.getDatum() + " " + schicht.getStartZeit().format(DateTimeFormatter.ofPattern("HH:mm")) + "-" + schicht.getEndZeit().format(DateTimeFormatter.ofPattern("HH:mm")) +
                                                    " (" + schicht.getSchichtTyp() + ", Ressort: " + schicht.getRessortBedarf() + ")")
                                    );
                                });
                    });
        }
    }

    /**
     * Konvertiert die OptaPlanner-Lösung in ein Format, das vom Frontend
     * (z.B. für die Mitarbeiterkalender-Anzeige) leicht verarbeitet werden kann.
     * Angepasst für SchichtBlöcke.
     */
    private List<Map<String, Object>> konvertiereLösungFuerFrontend(SchichtPlan solution) {
        Map<String, Map<String, Object>> mitarbeiterMap = new LinkedHashMap<>();
        // Deklariere die Liste hier, damit sie im Scope der Methode ist
        List<Map<String, Object>> mitarbeiterDaten = new ArrayList<>();

        for (Mitarbeiter m : solution.getMitarbeiterList()) {
            Map<String, Object> mitarbeiterData = new LinkedHashMap<>();
            mitarbeiterData.put("id", m.getId());
            mitarbeiterData.put("name", m.getVorname() + " " + m.getNachname());
            mitarbeiterData.put("ressort", m.getRessort()); // Ressort ist String
            mitarbeiterData.put("cvd", m.isCVD());
            mitarbeiterData.put("wochenstunden", m.getWochenstunden()); // Wochenstunden ist int
            mitarbeiterData.put("email", m.getEmail());
            mitarbeiterData.put("stellenbezeichnung", m.getStellenbezeichnung());
            mitarbeiterData.put("rollenUndQualifikationen", m.getRollenUndQualifikationen());
            mitarbeiterData.put("teamsUndZugehoerigkeiten", m.getTeamsUndZugehoerigkeiten());
            mitarbeiterData.put("notizen", m.getNotizen());
            mitarbeiterData.put("geplanteSchichten", new LinkedHashMap<String, List<Map<String, String>>>()); // Datum -> Liste von Schichten
            mitarbeiterMap.put(m.getId(), mitarbeiterData);
        }

        // Füge die geplanten Schichten aus den SchichtBlöcken hinzu
        if (solution.getSchichtBlockList() != null) {
            for (SchichtBlock block : solution.getSchichtBlockList()) {
                Mitarbeiter zugewiesenerMitarbeiter = block.getMitarbeiter();
                if (zugewiesenerMitarbeiter != null) {
                    Map<String, Object> mitarbeiterData = mitarbeiterMap.get(zugewiesenerMitarbeiter.getId());
                    if (mitarbeiterData != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, List<Map<String, String>>> geplanteSchichten =
                                (Map<String, List<Map<String, String>>>) mitarbeiterData.get("geplanteSchichten");

                        for (Schicht schicht : block.getSchichtenImBlock()) {
                            String datumKey = schicht.getDatum().format(DateTimeFormatter.ISO_LOCAL_DATE); // ISO-Format für Frontend
                            List<Map<String, String>> schichtenAmTag = geplanteSchichten.computeIfAbsent(datumKey, k -> new ArrayList<>());

                            Map<String, String> schichtDetails = new LinkedHashMap<>();
                            schichtDetails.put("start", schicht.getStartZeit().format(DateTimeFormatter.ISO_LOCAL_TIME));
                            schichtDetails.put("end", schicht.getEndZeit().format(DateTimeFormatter.ISO_LOCAL_TIME));
                            schichtDetails.put("typ", schicht.getSchichtTyp()); // SchichtTyp ist String
                            schichtDetails.put("ressort", schicht.getRessortBedarf()); // Ressort ist String
                            schichtenAmTag.add(schichtDetails);
                        }
                    }
                }
            }
        }

        // Konvertiere die Map zurück in eine Liste für das Frontend
        mitarbeiterDaten.addAll(mitarbeiterMap.values());
        return mitarbeiterDaten;
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
