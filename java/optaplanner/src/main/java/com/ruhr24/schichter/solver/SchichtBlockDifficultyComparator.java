package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.SchichtBlock;
import com.ruhr24.schichter.domain.SchichtTyp; // Wichtig: Enum importieren
import java.util.Comparator;

public class SchichtBlockDifficultyComparator implements Comparator<SchichtBlock> {

    @Override
    public int compare(SchichtBlock a, SchichtBlock b) {
        // Wir vergleichen die Blöcke anhand eines "Schwierigkeits-Gewichts".
        // Ein höheres Gewicht bedeutet schwieriger.
        // Wir sortieren absteigend (b, a), damit der schwierigste Block zuerst kommt.
        return Integer.compare(getDifficultyWeight(b), getDifficultyWeight(a));
    }

    private int getDifficultyWeight(SchichtBlock block) {
        // Hier definierst du deine Geschäftslogik für die Schwierigkeit.
        // Beispiel:
        String blockTyp = block.getBlockTyp();

        if (blockTyp.equals(SchichtTyp.CVD_WOCHENENDE_FRUEH_BLOCK.getDisplayValue()) ||
            blockTyp.equals(SchichtTyp.CVD_WOCHENENDE_SPAET_BLOCK.getDisplayValue())) {
            return 100; // Wochenend-CvD ist am schwierigsten
        }
        if (blockTyp.equals(SchichtTyp.CVD_FRUEHDIENST.getDisplayValue()) ||
            blockTyp.equals(SchichtTyp.CVD_SPAETDIENST.getDisplayValue())) {
            return 90; // Wochentags-CvD ist sehr schwierig
        }
        if (blockTyp.contains("Wochenend")) { // Alle anderen Wochenend-Dienste
            return 80;
        }
        if (blockTyp.equals(SchichtTyp.ADMIN_MO_FR_BLOCK.getDisplayValue())) {
            return 50; // Admin ist mittelschwer
        }
        // Alle anderen Standard-Kerndienste haben die niedrigste Schwierigkeit
        return 10;
    }
}