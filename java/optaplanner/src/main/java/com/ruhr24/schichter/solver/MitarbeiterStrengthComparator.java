package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import java.util.Comparator;
import java.util.List;

public class MitarbeiterStrengthComparator implements Comparator<Mitarbeiter> {

    @Override
    public int compare(Mitarbeiter a, Mitarbeiter b) {
        // Diese Logik ist der Kern der "fairen" Zuweisung.
        // Ein Mitarbeiter ist "schwächer" (und wird bevorzugt), wenn er weniger Muster zugewiesen hat.
        
        return Comparator
                // KORREKTUR: Wir sagen dem Compiler explizit, dass "m" ein Mitarbeiter ist.
                .comparingInt((Mitarbeiter m) -> m.getZugewieseneMuster() != null ? m.getZugewieseneMuster().size() : 0)
                // Wenn die Auslastung gleich ist, sortiere nach ID, um ein konsistentes Verhalten zu gewährleisten.
                .thenComparing(Mitarbeiter::getId)
                // Wende diesen Comparator nun auf die beiden Mitarbeiter a und b an.
                .compare(a, b);
    }
}