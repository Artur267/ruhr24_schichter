package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Arbeitsmuster;
import java.util.Comparator;

public class ArbeitsmusterDifficultyComparator implements Comparator<Arbeitsmuster> {

    @Override
    public int compare(Arbeitsmuster a, Arbeitsmuster b) {
        // Vergleicht Muster anhand eines "Schwierigkeits-Gewichts".
        // Eine höhere Zahl bedeutet schwieriger.
        // Wir sortieren absteigend (b vor a), damit die schwierigsten Muster zuerst geplant werden.
        return Integer.compare(getDifficultyWeight(b), getDifficultyWeight(a));
    }

    private int getDifficultyWeight(Arbeitsmuster muster) {
        String typ = muster.getMusterTyp();
        if (typ == null) {
            return 0;
        }
        
        // Wochenend-Dienste sind am schwierigsten
        if (typ.contains("_WE_")) { 
            return 100;
        }
        // Spätdienste sind schwieriger als Frühdienste
        if (typ.equals("CVD_SPAET")) { 
            return 50;
        }
        if (typ.equals("CVD_FRUEH")) {
            return 40;
        }
        // Spezifische Teilzeit-Muster sind wichtiger als generische
        if (typ.startsWith("TZ_")) { 
            return 90;
        }
        // Standard-Muster (Admin, CvD-Kern) sind am einfachsten
        return 10; 
    }
}