// src/main/java/com/ruhr24.schichter.controller/SolverController.java
package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.Schicht;

import com.ruhr24.schichter.dto.PlanungsanfrageDto;
import com.ruhr24.schichter.generator.SchichtBlockGenerator;
import com.ruhr24.schichter.solution.SolutionStore;

import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolverStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.HashSet;

@RestController
@RequestMapping("/api")
public class SolverController {

    @Autowired
    private SolverManager<SchichtPlan, UUID> solverManager;

    @Autowired
    private SchichtBlockGenerator schichtBlockGenerator;

    @Autowired
    private SolutionStore solutionStore;

    // Passe diesen Wert an den tatsächlichen spendLimit in deiner solverConfig.xml an!
    // Wenn in solverConfig.xml z.B. <spendLimit>18s</spendLimit> steht, dann hier 18 * 1000L.
    private static final long ACTUAL_SOLVER_TIMEOUT_MILLIS = 18 * 1000L; // Beispiel: 18 Sekunden

    /**
     * Startet eine asynchrone Planung für den Schichtplan.
     * Nimmt eine Planungsanfrage entgegen, generiert das Problem und startet den Solver.
     * Gibt sofort eine Problem-ID zurück, damit das Frontend den Status abfragen kann.
     *
     * @param requestDto Das DTO mit den Planungsdaten (von, bis, Mitarbeiterliste).
     * @return ResponseEntity mit der Problem-ID und Timeout-Informationen.
     */
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody PlanungsanfrageDto requestDto) {
        System.out.println("[JAVA BACKEND] Empfange Planungsanfrage für Zeitraum: " + requestDto.getVon() + " bis " + requestDto.getBis());

        LocalDate vonDatum = requestDto.getVon();
        LocalDate bisDatum = requestDto.getBis();
        List<Mitarbeiter> mitarbeiterList = requestDto.getMitarbeiterList();

        System.out.println("[JAVA BACKEND] Empfangen: " + mitarbeiterList.size() + " Mitarbeiter.");

        List<SchichtBlock> schichtBlocks = schichtBlockGenerator.generateSchichtBlocks(vonDatum, bisDatum);
        System.out.println("[JAVA BACKEND] Generierte " + schichtBlocks.size() + " Schichtblöcke.");

        List<Schicht> alleSchichten = schichtBlocks.stream()
                .flatMap(sb -> sb.getSchichtenImBlock().stream())
                .collect(Collectors.toList());
        System.out.println("[JAVA BACKEND] Gesammelte " + alleSchichten.size() + " einzelne Schichten.");

        SchichtPlan problem = new SchichtPlan(
            UUID.randomUUID(),
            vonDatum,
            bisDatum,
            requestDto.getRessort(),
            mitarbeiterList,
            alleSchichten,
            schichtBlocks,
            new HashSet<>()
        );

        UUID problemId = problem.getId();
        System.out.println("[JAVA BACKEND] Starte Solver asynchron mit Problem ID: " + problemId);

        solutionStore.putProblem(problemId, problem);

        final SolverJob<SchichtPlan, UUID>[] solverJobHolder = new SolverJob[1];

        try {
            solverJobHolder[0] = solverManager.solveAndListen(problemId,
                    (problemIdGiven) -> problem,
                    (SchichtPlan bestSolution) -> {
                        System.out.println("[JAVA BACKEND] Neue beste Lösung für ID " + problemId + " gefunden. Score: " + bestSolution.getScore());
                        bestSolution.getMitarbeiterList().forEach(mitarbeiter -> {
                            List<SchichtBlock> assignedBlocks = bestSolution.getSchichtBlockList().stream()
                                    .filter(sb -> sb.getMitarbeiter() != null && sb.getMitarbeiter().equals(mitarbeiter))
                                    .collect(Collectors.toList());
                            mitarbeiter.setAssignedSchichtBlocks(assignedBlocks);
                        });
                        solutionStore.putSolution(problemId, bestSolution);
                    },
                    (UUID terminatedProblemId, Throwable throwable) -> {
                        AtomicReference<SchichtPlan> finalOrLastBestSolutionRef = new AtomicReference<>();

                        if (throwable == null) {
                            try {
                                finalOrLastBestSolutionRef.set(solverJobHolder[0].getFinalBestSolution());
                                System.out.println("[JAVA BACKEND] Solver für ID " + terminatedProblemId + " erfolgreich beendet. Finale Lösung aus Job abgerufen.");
                            } catch (InterruptedException | ExecutionException e) {
                                System.err.println("[JAVA BACKEND] ⚠️ Warnung: Fehler beim Abrufen der finalen Lösung aus Job für ID " + terminatedProblemId + ": " + e.getMessage() + ". Verwende die letzte beste Zwischenlösung aus dem Store.");
                                finalOrLastBestSolutionRef.set(solutionStore.getSolution(terminatedProblemId));
                                solutionStore.putError(terminatedProblemId, new RuntimeException("Fehler beim Abrufen der finalen Lösung vom SolverJob.", e));
                            }
                        } else {
                            System.err.println("[JAVA BACKEND] ❌ Fehler während der Planung für ID: " + terminatedProblemId + ": " + throwable.getMessage());
                            throwable.printStackTrace();
                            if (throwable instanceof Exception) {
                                solutionStore.putError(terminatedProblemId, (Exception) throwable);
                            } else {
                                solutionStore.putError(terminatedProblemId, new RuntimeException("Unerwarteter Fehler: " + throwable.getMessage(), throwable));
                            }
                            finalOrLastBestSolutionRef.set(solutionStore.getSolution(terminatedProblemId));
                        }

                        SchichtPlan finalSolutionToStore = finalOrLastBestSolutionRef.get();
                        if (finalSolutionToStore != null) {
                            finalSolutionToStore.getMitarbeiterList().forEach(mitarbeiter -> {
                                List<SchichtBlock> assignedBlocks = finalSolutionToStore.getSchichtBlockList().stream()
                                        .filter(sb -> sb.getMitarbeiter() != null && sb.getMitarbeiter().equals(mitarbeiter))
                                        .collect(Collectors.toList());
                                mitarbeiter.setAssignedSchichtBlocks(assignedBlocks);
                            });
                            solutionStore.putSolution(terminatedProblemId, finalSolutionToStore);
                            System.out.println("[JAVA BACKEND] Finale (oder letzte beste) Lösung für ID " + terminatedProblemId + " im Store aktualisiert/gespeichert.");
                        } else {
                            System.err.println("[JAVA BACKEND] ⚠️ Warnung: Keine finale oder beste Zwischenlösung für ID " + terminatedProblemId + " zum Speichern im Store verfügbar nach Solver-Beendigung.");
                        }
                    });
        } catch (Exception e) {
            System.err.println("[JAVA BACKEND] ❗ Fehler beim Starten des Solvers für ID " + problemId + ": " + e.getMessage());
            solutionStore.putError(problemId, new RuntimeException("Fehler beim Starten des Solvers.", e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fehler beim Starten der Planung: " + e.getMessage(), "problemId", problemId.toString()));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("problemId", problemId.toString());
        response.put("solverTimeoutMillis", ACTUAL_SOLVER_TIMEOUT_MILLIS);
        response.put("message", "Planung mit ID " + problemId + " wurde gestartet.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Ruft den aktuellen Status oder die beste gefundene Lösung für eine gegebene Problem-ID ab.
     * @param problemId Die ID der laufenden oder abgeschlossenen Planung.
     * @return Die aktuelle beste Lösung oder einen Status-Hinweis.
     */
    @GetMapping("/planungs-ergebnis/{problemId}")
    public ResponseEntity<?> getSolution(@PathVariable UUID problemId) {
        System.out.println("[JAVA BACKEND] Anfrage für Ergebnis von ID: " + problemId);

        // Zuerst prüfen, ob ein Fehler aufgetreten ist (höchste Priorität)
        if (solutionStore.hasError(problemId)) {
            Exception error = solutionStore.getError(problemId);
            System.err.println("[JAVA BACKEND] Fehler für ID " + problemId + " gefunden: " + error.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fehler bei der Planung: " + error.getMessage(), "problemId", problemId.toString())); // Auch hier JSON
        }

        // --- NEUE, ROBUSTERE LOGIK START ---

        // 1. Prüfen, ob der SolutionStore bereits eine Lösung für diese ID hat (beste Zwischenlösung oder finale Lösung)
        if (solutionStore.hasSolution(problemId)) {
            SchichtPlan storedSolution = solutionStore.getSolution(problemId);
            SolverStatus currentSolverStatus = solverManager.getSolverStatus(problemId); // Holen den aktuellen Status

            if (currentSolverStatus == SolverStatus.NOT_SOLVING) {
                // Solver ist beendet UND wir haben eine Lösung im Store. Dies ist die finale Lösung.
                System.out.println("[JAVA BACKEND] Job " + problemId + " wurde beendet (Status NOT_SOLVING). Sende FINALE Lösung aus Store.");
                return ResponseEntity.ok(storedSolution);
            } else {
                // Solver läuft noch ODER ist in einem Übergangszustand, aber wir haben eine Zwischenlösung.
                System.out.println("[JAVA BACKEND] Job " + problemId + " läuft noch. Sende ZWISCHENLÖSUNG. Status: " + currentSolverStatus);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(storedSolution);
            }
        }

        // 2. Wenn der SolutionStore KEINE Lösung hat, prüfen, ob der Solver noch aktiv ist.
        // Dies sollte nur sehr früh in der Planung vorkommen oder wenn ein Fehler auftritt,
        // bevor eine erste Lösung gefunden und gespeichert werden konnte.
        SolverStatus currentSolverStatus = solverManager.getSolverStatus(problemId);

        if (currentSolverStatus != SolverStatus.NOT_SOLVING) {
            // Solver ist aktiv, hat aber noch keine (erste) Lösung in den Store geschrieben.
            System.out.println("[JAVA BACKEND] Solver für ID " + problemId + " läuft noch, aber keine Lösung im Store vorhanden. Status: " + currentSolverStatus);
            // KORREKTUR: Sende ein JSON-Objekt, nicht nur einen String!
            Map<String, String> statusBody = new HashMap<>();
            statusBody.put("message", "Solving in progress for ID: " + problemId + ". Aktueller Status: " + currentSolverStatus + ". Bitte warten Sie auf die erste Lösung.");
            statusBody.put("status", currentSolverStatus.name()); // Fügen Sie den Status als String hinzu
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(statusBody);
        } else {
            // Solver ist NICHT aktiv (NOT_SOLVING) UND der Store hat KEINE Lösung und KEINEN Fehler.
            // Dies ist der unwahrscheinlichste und problematischste Fall.
            System.err.println("[JAVA BACKEND] Job " + problemId + " nicht gefunden oder nicht mehr aktiv (Status NOT_SOLVING) UND keine Lösung oder Fehler im Store vorhanden.");
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("error", "Planungs-Job mit ID " + problemId + " wurde nicht gefunden oder ist nicht mehr aktiv oder konnte keine Lösung speichern.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody); // Auch hier JSON
        }
        // --- NEUE, ROBUSTERE LOGIK ENDE ---
    }
}
