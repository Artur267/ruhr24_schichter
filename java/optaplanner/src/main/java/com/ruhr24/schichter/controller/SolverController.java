package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Arbeitsmuster;
import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.dto.PlanungsanfrageDto;
import com.ruhr24.schichter.generator.MusterGenerator;
//import com.ruhr24.schichter.generator.SchichtGenerator;
import com.ruhr24.schichter.solution.SolutionStore;
import org.optaplanner.core.api.score.ScoreManager;


//import org.optaplanner.core.api.score.explanation.ScoreExplanation;

import org.optaplanner.core.api.score.ScoreExplanation;
import org.optaplanner.core.api.score.ScoreManager;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.solver.SolutionManager;
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
//import java.time.DayOfWeek;
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

    private final SolverManager<SchichtPlan, UUID> solverManager;
    private final SolutionManager<SchichtPlan, HardSoftLongScore> solutionManager; // NEU
    private final MusterGenerator musterGenerator;
    private final SolutionStore solutionStore;

    private static final long ACTUAL_SOLVER_TIMEOUT_MILLIS = 600 * 1000L;

    @Autowired
    public SolverController(SolverManager<SchichtPlan, UUID> solverManager, SolutionManager<SchichtPlan, HardSoftLongScore> solutionManager, SolutionStore solutionStore) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager; // NEU
        this.solutionStore = solutionStore;
        this.musterGenerator = new MusterGenerator();
    }
    
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody PlanungsanfrageDto requestDto) {
        System.out.println("[TEST] Wünsche erhalten: " + (requestDto.getWuensche() != null ? requestDto.getWuensche().size() : "keine"));
        LocalDate vonDatum = requestDto.getVon();
        LocalDate bisDatum = requestDto.getBis();

        List<Arbeitsmuster> muster = musterGenerator.generate(vonDatum, bisDatum, requestDto.getMitarbeiterList(), requestDto.getWuensche());
        SchichtPlan problem = new SchichtPlan(
            UUID.randomUUID(),
            vonDatum,
            bisDatum,
            requestDto.getRessort(),
            requestDto.getMitarbeiterList(),
            muster, // Wichtig: hier die Musterliste übergeben
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
        if (throwable != null) {
            // Falls der Solver selbst abgestürzt ist, logge den Fehler.
            System.err.println("[JAVA BACKEND] ❌ Solver für ID " + problemId + " ist mit einem Fehler beendet worden.");
            throwable.printStackTrace();
            solutionStore.putError(problemId, new RuntimeException("Solver-Fehler.", throwable));
        }

        // Hole die letzte bekannte beste Lösung aus unserem Zwischenspeicher.
        SchichtPlan finalSolution = solutionStore.getSolution(problemId);

        if (finalSolution != null) {
            // Rufe direkt die Speicherfunktion auf.
            saveSolutionToCsv(finalSolution);

            // Optional: Gib die Score-Analyse aus, um zu sehen, wie gut der Plan ist.
            ScoreExplanation<SchichtPlan, HardSoftLongScore> scoreExplanation = solutionManager.explain(finalSolution);
            System.out.println("\n--- FINALE SCORE-ANALYSE FÜR " + problemId + " ---\n" + scoreExplanation.getSummary());

        } else {
            System.err.println("[JAVA BACKEND] Konnte keine finale Lösung zum Speichern für ID " + problemId + " finden.");
        }
    }

    @GetMapping("/planungs-ergebnis/{problemId}")
    public ResponseEntity<?> getSolution(@PathVariable UUID problemId) {
        // Prüfe zuerst auf Fehler
        if (solutionStore.hasError(problemId)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", solutionStore.getError(problemId).getMessage()));
        }

        SchichtPlan solution = solutionStore.getSolution(problemId);
        SchichtPlan problem = solutionStore.getProblem(problemId);
        SolverStatus status = solverManager.getSolverStatus(problemId);

        // Fall 1: Weder Problem noch Lösung bekannt -> Echter 404
        if (problem == null && solution == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Planung mit dieser ID existiert nicht."));
        }

        // Fall 2: Der Solver ist fertig.
        if (status == SolverStatus.NOT_SOLVING) {
            return ResponseEntity.ok(solution); // Gib die finale Lösung zurück
        }

        // Fall 3: Der Solver läuft noch. Gib eine "Accepted"-Antwort mit dem Zwischenstand.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(solution != null ? solution : problem);
    }
    
    private void saveSolutionToCsv(SchichtPlan schichtPlan) {
        String filePath = "java/optaplanner/results/output.csv";
        File outputFile = new File(filePath);

        if (schichtPlan == null || schichtPlan.getVon() == null || schichtPlan.getBis() == null) {
            System.err.println("[CSV-Fehler] Schichtplan oder dessen Daten sind null. Breche CSV-Erstellung ab.");
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // 1. Header erstellen (bleibt unverändert)
            List<String> header = new ArrayList<>(List.of("NutzerID", "Nachname", "Vorname", "E-Mail", "Stellenbezeichnung", "Ressort", "CVD", "Qualifikationen", "Teams", "Notizen", "Wochenstunden", "MonatsSumme", "Delta"));
            List<LocalDate> allDatesInPlan = schichtPlan.getVon().datesUntil(schichtPlan.getBis().plusDays(1)).collect(Collectors.toList());
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.");
            for (LocalDate date : allDatesInPlan) {
                header.add(date.format(dateFormatter) + " Von");
                header.add(date.format(dateFormatter) + " Bis");
            }
            writer.println(String.join(";", header));

            // 2. KORREKTUR: Alle zugewiesenen Schichten aus den Mustern entpacken
            List<Schicht> alleZugewiesenenSchichten = schichtPlan.getArbeitsmusterList().stream()
                .filter(muster -> muster.getMitarbeiter() != null)
                .flatMap(muster -> {
                    // WICHTIG: Füge den Mitarbeiter vom Muster zu jeder Einzelschicht hinzu!
                    muster.getSchichten().forEach(schicht -> schicht.setMitarbeiter(muster.getMitarbeiter()));
                    return muster.getSchichten().stream();
                })
                .collect(Collectors.toList());

            // 3. Gruppiere diese flache Liste von Schichten nach Mitarbeiter
            Map<Mitarbeiter, List<Schicht>> schichtenProMitarbeiter = alleZugewiesenenSchichten.stream()
                .collect(Collectors.groupingBy(Schicht::getMitarbeiter));

            // 4. Schreibe die CSV-Zeilen (diese Logik ist jetzt wieder korrekt)
            for (Mitarbeiter mitarbeiter : schichtPlan.getMitarbeiterList()) {
                List<String> rowData = new ArrayList<>();
                
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

                List<Schicht> zugewieseneSchichten = schichtenProMitarbeiter.getOrDefault(mitarbeiter, Collections.emptyList());
                double totalHours = zugewieseneSchichten.stream().mapToLong(Schicht::getArbeitszeitInMinuten).sum() / 60.0;
                double targetHours = (double) (mitarbeiter.getWochenstunden() * 4); // Annahme: 4 Wochen Plan
                double delta = totalHours - targetHours;
                
                rowData.add(String.format(Locale.GERMANY, "%.2f", totalHours));
                rowData.add(String.format(Locale.GERMANY, "%.2f", delta));

                Map<LocalDate, Schicht> tagesSchichtMap = zugewieseneSchichten.stream()
                        .collect(Collectors.toMap(Schicht::getDatum, Function.identity(), (s1, s2) -> s1));

                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                for (LocalDate date : allDatesInPlan) {
                    Schicht schichtAnDiesemTag = tagesSchichtMap.get(date);
                    if (schichtAnDiesemTag != null) {
                        rowData.add(schichtAnDiesemTag.getStartZeit().format(timeFormatter));
                        rowData.add(schichtAnDiesemTag.getEndZeit().format(timeFormatter));
                    } else {
                        rowData.add("");
                        rowData.add("");
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