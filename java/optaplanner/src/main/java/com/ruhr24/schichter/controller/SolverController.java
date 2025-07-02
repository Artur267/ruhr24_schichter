package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.dto.PlanungsanfrageDto;
import com.ruhr24.schichter.generator.SchichtGenerator;
import com.ruhr24.schichter.solution.SolutionStore;

import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SolverController {

    @Autowired
    private SolverManager<SchichtPlan, UUID> solverManager;

    private final SchichtGenerator schichtGenerator;

    @Autowired
    private SolutionStore solutionStore;

    private static final long ACTUAL_SOLVER_TIMEOUT_MILLIS = 600 * 1000L;

    public SolverController(SolverManager<SchichtPlan, UUID> solverManager, SolutionStore solutionStore) {
        this.solverManager = solverManager;
        this.solutionStore = solutionStore;
        this.schichtGenerator = new SchichtGenerator();
    }
    
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody PlanungsanfrageDto requestDto) {
        LocalDate vonDatum = requestDto.getVon();
        LocalDate bisDatum = requestDto.getBis();

        List<Schicht> schichten = schichtGenerator.generate(vonDatum, bisDatum, requestDto.getMitarbeiterList());
        SchichtPlan problem = new SchichtPlan(
            UUID.randomUUID(),
            vonDatum,
            bisDatum,
            requestDto.getRessort(),
            requestDto.getMitarbeiterList(),
            schichten,
            new HashSet<>()
        );

        UUID problemId = problem.getId();
        solutionStore.putProblem(problemId, problem);

        @SuppressWarnings("unchecked")
        final SolverJob<SchichtPlan, UUID>[] solverJobHolder = (SolverJob<SchichtPlan, UUID>[]) new SolverJob[1];
        try {
            solverJobHolder[0] = solverManager.solveAndListen(problemId,
                    id -> problem,
                    bestSolution -> {
                        // NUR ZWISCHENSPEICHERN, NICHT MEHR DIE CSV SCHREIBEN
                        solutionStore.putSolution(problemId, bestSolution);
                        System.out.println("[JAVA BACKEND] Neue beste Lösung für ID " + problemId + " gefunden. Score: " + bestSolution.getScore());
                    },
                    (terminatedProblemId, throwable) -> {
                        // DAS FINALE SPEICHERN PASSIERT NUR NOCH HIER
                        handleTermination(terminatedProblemId, throwable, solverJobHolder[0]);
                    });
        } catch (Exception e) {
            solutionStore.putError(problemId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> response = Map.of(
            "problemId", problemId.toString(),
            "solverTimeoutMillis", ACTUAL_SOLVER_TIMEOUT_MILLIS,
            "message", "Planung gestartet."
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private void handleTermination(UUID problemId, Throwable throwable, SolverJob<SchichtPlan, UUID> job) {
        AtomicReference<SchichtPlan> finalSolutionRef = new AtomicReference<>();
        if (throwable == null) {
            try {
                finalSolutionRef.set(job.getFinalBestSolution());
            } catch (InterruptedException | ExecutionException e) {
                finalSolutionRef.set(solutionStore.getSolution(problemId));
                solutionStore.putError(problemId, new RuntimeException("Fehler beim Abrufen der finalen Lösung.", e));
            }
        } else {
            System.err.println("[JAVA BACKEND] ❌ Solver-Fehler für ID: " + problemId);
            throwable.printStackTrace();
            solutionStore.putError(problemId, new RuntimeException("Solver-Fehler.", throwable));
            finalSolutionRef.set(solutionStore.getSolution(problemId));
        }
        
        SchichtPlan finalSolution = finalSolutionRef.get();
        if (finalSolution != null) {
            solutionStore.putSolution(problemId, finalSolution);
            // HIER WIRD DIE CSV JETZT NUR NOCH EINMAL GERUFEN
            saveSolutionToCsv(finalSolution);
        }
    }

    @GetMapping("/planungs-ergebnis/{problemId}")
    public ResponseEntity<?> getSolution(@PathVariable UUID problemId) {
        if (solutionStore.hasError(problemId)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", solutionStore.getError(problemId).getMessage()));
        }
        if (solutionStore.hasSolution(problemId)) {
            SchichtPlan solution = solutionStore.getSolution(problemId);
            SolverStatus status = solverManager.getSolverStatus(problemId);
            return (status == SolverStatus.NOT_SOLVING) ? ResponseEntity.ok(solution) : ResponseEntity.status(HttpStatus.ACCEPTED).body(solution);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Planung nicht gefunden."));
    }
    
    private void saveSolutionToCsv(SchichtPlan schichtPlan) {
        // Sicherstellen, dass der Ordner 'results' existiert
        File resultsDir = new File("results");
        if (!resultsDir.exists()) resultsDir.mkdirs();

        // Wir nutzen die feste output.csv, damit dein Frontend sie direkt findet
        File outputFile = new File(resultsDir, "output.csv");

        // Null-Check, um Abstürze zu verhindern
        if (schichtPlan == null || schichtPlan.getVon() == null || schichtPlan.getBis() == null) {
            System.err.println("[CSV-Fehler] Schichtplan oder dessen Daten sind null. Breche CSV-Erstellung ab.");
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // 1. Erstelle den kompletten Header, den dein server.js und dein Backup erwarten
            List<String> header = new ArrayList<>(List.of(
                    "NutzerID", "Nachname", "Vorname", "E-Mail", "Stellenbezeichnung",
                    "Ressort", "CVD", "Qualifikationen", "Teams", "Notizen",
                    "Wochenstunden", "MonatsSumme", "Delta"
            ));
            List<LocalDate> allDatesInPlan = schichtPlan.getVon().datesUntil(schichtPlan.getBis().plusDays(1)).collect(Collectors.toList());
            
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.");
            for (LocalDate date : allDatesInPlan) {
                header.add(date.format(dateFormatter) + " Von");
                header.add(date.format(dateFormatter) + " Bis");
            }
            writer.println(String.join(";", header));

            // 2. Berechne die Anzahl der Werktage für die Delta-Berechnung
            int anzahlWerktageImPlan = 0;
             for (LocalDate date = schichtPlan.getVon(); !date.isAfter(schichtPlan.getBis()); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && 
                   (schichtPlan.getPublicHolidays() == null || !schichtPlan.getPublicHolidays().contains(date))) {
                    anzahlWerktageImPlan++;
                }
            }

            // 3. Sammle die zugewiesenen Schichten pro Mitarbeiter
            Map<Mitarbeiter, List<Schicht>> schichtenProMitarbeiter = schichtPlan.getSchichtList().stream()
                .filter(s -> s.getMitarbeiter() != null)
                .collect(Collectors.groupingBy(Schicht::getMitarbeiter));

            // 4. Schreibe für jeden Mitarbeiter eine Zeile mit allen Daten
            for (Mitarbeiter mitarbeiter : schichtPlan.getMitarbeiterList()) {
                List<String> rowData = new ArrayList<>();
                
                // Füge alle Stammdaten hinzu, die dein Frontend erwartet
                rowData.add(mitarbeiter.getId() != null ? mitarbeiter.getId() : "");
                rowData.add(mitarbeiter.getNachname() != null ? mitarbeiter.getNachname() : "");
                rowData.add(mitarbeiter.getVorname() != null ? mitarbeiter.getVorname() : "");
                rowData.add(mitarbeiter.getEmail() != null ? mitarbeiter.getEmail() : "");
                rowData.add(mitarbeiter.getStellenbezeichnung() != null ? mitarbeiter.getStellenbezeichnung() : "");
                rowData.add(mitarbeiter.getRessort() != null ? mitarbeiter.getRessort() : "");
                rowData.add(String.valueOf(mitarbeiter.isCVD()));
                rowData.add(mitarbeiter.getRollenUndQualifikationen() != null ? String.join(", ", mitarbeiter.getRollenUndQualifikationen()) : "");
                rowData.add(mitarbeiter.getTeamsUndZugehoerigkeiten() != null ? String.join(", ", mitarbeiter.getTeamsUndZugehoerigkeiten()) : "");
                rowData.add(mitarbeiter.getNotizen() != null ? mitarbeiter.getNotizen() : "");
                rowData.add(String.valueOf(mitarbeiter.getWochenstunden()));

                // Berechne die Stunden
                List<Schicht> zugewieseneSchichten = schichtenProMitarbeiter.getOrDefault(mitarbeiter, Collections.emptyList());
                double totalHours = zugewieseneSchichten.stream().mapToLong(Schicht::getArbeitszeitInMinuten).sum() / 60.0;
                double targetHours = (mitarbeiter.getWochenstunden() / 5.0) * anzahlWerktageImPlan;
                double delta = totalHours - targetHours;
                
                rowData.add(String.format(Locale.GERMANY, "%.2f", totalHours));
                rowData.add(String.format(Locale.GERMANY, "%.2f", delta));

                // Erstelle eine Map für schnellen Zugriff auf die Schicht eines Tages
                Map<LocalDate, Schicht> tagesSchichtMap = zugewieseneSchichten.stream()
                        .collect(Collectors.toMap(Schicht::getDatum, Function.identity(), (s1, s2) -> s1));

                // Füge die Schichtzeiten für jeden Tag hinzu
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                for (LocalDate date : allDatesInPlan) {
                    Schicht schichtAnDiesemTag = tagesSchichtMap.get(date);
                    if (schichtAnDiesemTag != null) {
                        rowData.add(schichtAnDiesemTag.getStartZeit().format(timeFormatter));
                        rowData.add(schichtAnDiesemTag.getEndZeit().format(timeFormatter));
                    } else {
                        rowData.add(""); // Leere Spalte für "Von"
                        rowData.add(""); // Leere Spalte für "Bis"
                    }
                }
                writer.println(String.join(";", rowData));
            }
            System.out.println("[JAVA BACKEND] CSV-Lösung erfolgreich gespeichert: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[JAVA BACKEND] Fehler beim Speichern der CSV-Lösung: " + e.getMessage());
            e.printStackTrace();
        }
    }
}