// src/main/java/com/ruhr24.schichter.solution/SolutionStore.java
package com.ruhr24.schichter.solution;

import com.ruhr24.schichter.domain.SchichtPlan; // Passe dies an deine Domain-Klasse an

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component // Wichtig, damit Spring diese Klasse als Bean erkennt und automatisch instanziiert
public class SolutionStore {

    // Speichert die letzte beste Lösung für jede Problem-ID
    private final Map<UUID, SchichtPlan> solutions = new ConcurrentHashMap<>();
    // Speichert das initiale Problem für jede Problem-ID (optional, für Debugging)
    private final Map<UUID, SchichtPlan> problems = new ConcurrentHashMap<>();
    // Speichert Fehler, die während des Lösens aufgetreten sind
    private final Map<UUID, Exception> errors = new ConcurrentHashMap<>();

    // Methode zum Speichern des initialen Problems
    public void putProblem(UUID problemId, SchichtPlan problem) {
        problems.put(problemId, problem);
    }

    // Methode zum Abrufen des initialen Problems
    public SchichtPlan getProblem(UUID problemId) {
        return problems.get(problemId);
    }

    // Methode zum Speichern einer (neuen besten) Lösung
    public void putSolution(UUID problemId, SchichtPlan solution) {
        solutions.put(problemId, solution);
    }

    // Methode zum Abrufen der besten verfügbaren Lösung
    public SchichtPlan getSolution(UUID problemId) {
        return solutions.get(problemId);
    }

    // Prüft, ob eine Lösung für diese Problem-ID vorhanden ist
    public boolean hasSolution(UUID problemId) {
        return solutions.containsKey(problemId);
    }

    // Methode zum Speichern eines Fehlers
    public void putError(UUID problemId, Exception error) {
        errors.put(problemId, error);
    }

    // Methode zum Abrufen eines Fehlers
    public Exception getError(UUID problemId) {
        return errors.get(problemId);
    }

    // Prüft, ob ein Fehler für diese Problem-ID vorhanden ist
    public boolean hasError(UUID problemId) {
        return errors.containsKey(problemId);
    }

    // Optional: Methode zum Aufräumen von Einträgen nach Abschluss der Planung
    public void cleanup(UUID problemId) {
        problems.remove(problemId);
        solutions.remove(problemId);
        errors.remove(problemId);
        System.out.println("Cleaned up solution store for problemId: " + problemId);
    }
}