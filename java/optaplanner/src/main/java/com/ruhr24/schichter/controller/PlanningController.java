package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.domain.Zuteilung;
import com.ruhr24.schichter.dto.PlanungsanfrageDto;

import org.optaplanner.core.api.solver.SolverJob;
import org.optaplanner.core.api.solver.SolverManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.Set; // Neu importieren
import java.util.stream.Collectors; // Neu importieren

@RestController
@RequestMapping("/api")
public class PlanningController {

    private final SolverManager<SchichtPlan, UUID> solverManager;
    private final Map<UUID, SchichtPlan> solvedPlans = new ConcurrentHashMap<>();
    private final Map<UUID, SolverJob<SchichtPlan, UUID>> activeSolverJobs = new ConcurrentHashMap<>();


    @Autowired
    public PlanningController(SolverManager<SchichtPlan, UUID> solverManager) {
        this.solverManager = solverManager;
    }

    @PostMapping("/planen")
    public ResponseEntity<String> planeSchichten(@RequestBody PlanungsanfrageDto anfrageDto) {
        UUID problemId = UUID.randomUUID();
        System.out.println("[JAVA BACKEND] 🧠 Starte Planung mit ID: " + problemId);

        // Das initiale Problemobjekt erstellen
        SchichtPlan initialProblem = bauePlanungsproblem(anfrageDto, problemId);

        Function<UUID, SchichtPlan> problemSupplier = currentProblemId -> {
            System.out.println("[JAVA BACKEND] Baue Planungsproblem für ID: " + currentProblemId);
            return initialProblem;
        };

        Consumer<SchichtPlan> bestSolutionConsumer = bestSolution -> {
            // Optional: Logge hier die besten Zwischenergebnisse
        };

        Consumer<SchichtPlan> finalSolutionConsumer = finalSolution -> {
            System.out.println("[JAVA BACKEND] ✅ Finale Lösung empfangen für ID: " + problemId + " - Consumer gestartet.");
            gibLösungsLoggingAus(finalSolution);
            solvedPlans.put(problemId, finalSolution);
            activeSolverJobs.remove(problemId);
            System.out.println("[JAVA BACKEND] ✅ Finale Lösung empfangen für ID: " + problemId + " - Consumer beendet.");
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

    private SchichtPlan bauePlanungsproblem(PlanungsanfrageDto anfrageDto, UUID problemId) {
        SchichtPlan plan = new SchichtPlan();
        plan.setId(problemId);

        if (anfrageDto.getVon() == null || anfrageDto.getBis() == null) {
            System.err.println("[JAVA BACKEND] FEHLER: Planungszeitraum fehlt im DTO! Kann kein Problem bauen.");
            throw new IllegalArgumentException("Planungszeitraum (von/bis) darf nicht null sein.");
        }

        LocalDate start = anfrageDto.getVon();
        LocalDate end = anfrageDto.getBis();
        // String ressort = anfrageDto.getRessort(); // Wird jetzt dynamisch ermittelt
        List<Mitarbeiter> mitarbeiterList = anfrageDto.getMitarbeiterList();

        plan.setVon(start);
        plan.setBis(end);
        // plan.setRessort(ressort); // Entfällt, da wir alle Ressorts abdecken
        plan.setMitarbeiterList(mitarbeiterList);

        System.out.println("[JAVA BACKEND] Mitarbeiter erhalten: " + (mitarbeiterList != null ? mitarbeiterList.size() : 0));
        if (mitarbeiterList != null) {
            mitarbeiterList.forEach(m -> System.out.println("[JAVA BACKEND]  - " + m.getVorname() + " " + m.getNachname() + ", Ressort: " + m.getRessort() + ", Wochenstunden: " + m.getWochenstunden()));
        }

        // NEUE LOGIK: Ermittle alle einzigartigen Ressorts aus den Mitarbeitern
        Set<String> alleRessorts = mitarbeiterList.stream()
                                            .map(Mitarbeiter::getRessort)
                                            .collect(Collectors.toSet());
        System.out.println("[JAVA BACKEND] Gefundene Ressorts in Mitarbeiterdaten: " + alleRessorts);


        List<Schicht> schichten = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                // Für jedes identifizierte Ressort eine Schicht generieren
                for (String ressortBedarf : alleRessorts) {
                    // Beispiel: Erstelle EINE Schicht pro Tag pro Ressort
                    // Du kannst hier weitere Schichtzeiten oder Anzahl benötigter Mitarbeiter anpassen
                    schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(6, 0), LocalTime.of(14, 30), ressortBedarf, 1));
                    // Optional: Eine zweite Schicht pro Tag pro Ressort, wenn mehr Bedarf ist
                    // schichten.add(new Schicht(UUID.randomUUID(), date, LocalTime.of(13, 30), LocalTime.of(23, 0), ressortBedarf, 1));
                }
            }
        }
        plan.setSchichtList(schichten);
        System.out.println("[JAVA BACKEND] Generierte Schichten: " + schichten.size());

        List<Zuteilung> zuteilungen = new ArrayList<>();
        for (Schicht schicht : schichten) {
            for (int i = 0; i < schicht.getBenötigteMitarbeiter(); i++) {
                Zuteilung z = new Zuteilung();
                z.setId(UUID.randomUUID());
                z.setSchicht(schicht);
                zuteilungen.add(z);
            }
        }
        plan.setZuteilungList(zuteilungen);
        System.out.println("[JAVA BACKEND] Zuteilungs-Slots erstellt: " + zuteilungen.size());

        return plan;
    }

    private void gibLösungsLoggingAus(SchichtPlan plan) {
        System.out.println("[JAVA BACKEND] --- Ergebnis ---");
        System.out.println("[JAVA BACKEND] Score: " + (plan.getScore() != null ? plan.getScore().toString() : "N/A"));

        if (plan.getZuteilungList() != null && !plan.getZuteilungList().isEmpty()) {
            plan.getZuteilungList().stream()
                .sorted((z1, z2) -> {
                    int dateCompare = z1.getSchicht().getDatum().compareTo(z2.getSchicht().getDatum());
                    if (dateCompare != 0) return dateCompare;
                    return z1.getSchicht().getStartZeit().compareTo(z2.getSchicht().getStartZeit());
                })
                .forEach(z -> {
                    String mitarbeiter = (z.getMitarbeiter() != null)
                            ? z.getMitarbeiter().getVorname() + " " + z.getMitarbeiter().getNachname()
                            : "UNBESETZT";
                    Schicht s = z.getSchicht();
                    String schichtDetails = (s != null)
                            ? s.getDatum() + " " + s.getStartZeit() + "-" + s.getEndZeit() + " (" + s.getRessortBedarf() + ")"
                            : "Keine Schicht zugewiesen";
                    System.out.println("[JAVA BACKEND] Zuteilung: " + mitarbeiter + " -> " + schichtDetails);
                });
        } else {
            System.out.println("[JAVA BACKEND] Keine Zuteilungen im Ergebnis gefunden oder Zuteilungsliste ist leer.");
        }
        System.out.println("[JAVA BACKEND] Planung erfolgreich beendet.");
    }
}