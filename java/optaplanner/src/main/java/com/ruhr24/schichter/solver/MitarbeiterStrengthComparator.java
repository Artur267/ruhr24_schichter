package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import java.util.Comparator;

public class MitarbeiterStrengthComparator implements Comparator<Mitarbeiter> {

    @Override
    public int compare(Mitarbeiter a, Mitarbeiter b) {
        // Vergleicht die Anzahl der bereits zugewiesenen Schichtblöcke.
        // Der Mitarbeiter mit WENIGER Blöcken ist "schwächer" und wird für die nächste Zuweisung bevorzugt.
        // Dies sorgt für den "Round Robin" / Fairness-Effekt.
        return Comparator
                .comparingInt((Mitarbeiter mitarbeiter) -> mitarbeiter.getAssignedSchichtBlocks().size())
                .thenComparing(Mitarbeiter::getId) // Stabile Sortierung bei gleicher Auslastung
                .compare(a, b);
    }
}