package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Schicht;
import java.time.DayOfWeek;
import java.util.Comparator;

public class SchichtDifficultyComparator implements Comparator<Schicht> {

    @Override
    public int compare(Schicht a, Schicht b) {
        // Vergleicht Schichten anhand eines "Schwierigkeits-Gewichts".
        // Eine höhere Zahl bedeutet schwieriger.
        // Wir sortieren absteigend, damit die schwierigste Schicht zuerst geplant wird.
        return Integer.compare(getDifficultyWeight(b), getDifficultyWeight(a));
    }

    private int getDifficultyWeight(Schicht schicht) {
        int weight = 0;
        // Wochenend-Schichten sind am schwierigsten zu besetzen.
        if (schicht.getDatum().getDayOfWeek() == DayOfWeek.SATURDAY || schicht.getDatum().getDayOfWeek() == DayOfWeek.SUNDAY) {
            weight += 100;
        }
        // CvD-Dienste sind schwieriger als normale Kerndienste.
        if (schicht.getSchichtTyp().contains("CVD")) {
            weight += 50;
        }
        return weight;
    }
}