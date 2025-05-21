package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;
import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.domain.Zuteilung;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// WICHTIG: Stelle sicher, dass Schicht.java die getDauerInStunden() Methode hat:
// import java.time.temporal.ChronoUnit;
// public double getDauerInStunden() { return (double) ChronoUnit.MINUTES.between(startZeit, endZeit) / 60.0; }

public class SimpleScoreCalculator implements EasyScoreCalculator<SchichtPlan, HardSoftScore> {

    @Override
    public HardSoftScore calculateScore(SchichtPlan plan) {
        int hardScore = 0;
        int softScore = 0;

        // --- HARD CONSTRAINTS ---

        // 1. Jede Zuteilung muss besetzt sein (Schicht muss besetzt sein)
        // Strafe: Wenn ein Zuteilungsslot keinen Mitarbeiter hat, ist das ein schwerwiegender Fehler.
        for (Zuteilung zuteilung : plan.getZuteilungList()) {
            if (zuteilung.getMitarbeiter() == null) {
                hardScore -= 100000; // Sehr hohe Strafe für unbesetzte Schichten
            }
        }

        // Map zum Speichern der zugewiesenen Stunden pro Mitarbeiter
        Map<Mitarbeiter, Double> zugewieseneStundenProMitarbeiter = new HashMap<>();
        // Set zum Speichern der Mitarbeiter, die tatsächlich einer Zuteilung zugewiesen wurden
        Set<Mitarbeiter> eingesetzteMitarbeiter = new HashSet<>();


        // Gehe alle Zuteilungen durch, um Stunden zu sammeln und Überschneidungen/Ressort-Kompatibilität zu prüfen
        for (Zuteilung zuteilung : plan.getZuteilungList()) {
            Mitarbeiter mitarbeiter = zuteilung.getMitarbeiter();
            Schicht schicht = zuteilung.getSchicht();

            if (mitarbeiter != null) {
                // Füge Mitarbeiter zu den eingesetzten Mitarbeitern hinzu
                eingesetzteMitarbeiter.add(mitarbeiter);

                // Sammle zugewiesene Stunden für jeden Mitarbeiter
                double dauer = schicht.getDauerInStunden();
                zugewieseneStundenProMitarbeiter.put(mitarbeiter,
                        zugewieseneStundenProMitarbeiter.getOrDefault(mitarbeiter, 0.0) + dauer);

                // 2. Überschneidende Schichten für denselben Mitarbeiter am selben Tag (Hard Constraint)
                // Dies ist dein bestehender Code, aber mit höherer Strafe
                for (Zuteilung andereZuteilung : plan.getZuteilungList()) {
                    if (zuteilung == andereZuteilung) continue; // Nicht mit sich selbst vergleichen

                    if (mitarbeiter.equals(andereZuteilung.getMitarbeiter())) { // Derselbe Mitarbeiter
                        if (schicht.getDatum().equals(andereZuteilung.getSchicht().getDatum())) { // Am selben Tag
                            // Prüfe auf Zeitüberschneidung
                            if (schicht.getStartZeit().isBefore(andereZuteilung.getSchicht().getEndZeit()) &&
                                andereZuteilung.getSchicht().getStartZeit().isBefore(schicht.getEndZeit())) {
                                hardScore -= 50000; // Hohe Strafe für Überschneidungen
                            }
                        }
                    }
                }

                // 3. Mitarbeiter muss für das Ressort der Schicht qualifiziert sein (Hard Constraint)
                if (!mitarbeiter.getRessort().equals(schicht.getRessortBedarf())) {
                    hardScore -= 20000; // Hohe Strafe, wenn Mitarbeiter nicht im richtigen Ressort ist
                }
            }
        }

        // --- HARD CONSTRAINTS (basierend auf aggregierten Daten) ---

        // 4. Mitarbeiter darf seine maximalen Wochenstunden NICHT überschreiten (Hard Constraint)
        for (Map.Entry<Mitarbeiter, Double> entry : zugewieseneStundenProMitarbeiter.entrySet()) {
            Mitarbeiter mitarbeiter = entry.getKey();
            Double zugewieseneStunden = entry.getValue();

            if (zugewieseneStunden > mitarbeiter.getWochenstunden()) {
                // Bestrafe die Überschreitung. Je mehr Stunden überschritten werden, desto höher die Strafe.
                hardScore -= (int) ((zugewieseneStunden - mitarbeiter.getWochenstunden()) * 100); // z.B. 100 Punkte pro Stunde Überstunde
            }
        }

        // --- SOFT CONSTRAINTS ---

        // 1. Mitarbeiter soll seine Wochenstunden MÖGLICHST erreichen (Soft Constraint)
        // Gehe alle potenziellen Mitarbeiter durch (nicht nur die eingesetzten), um auch ungenutzte zu erfassen.
        // Annahme: plan.getMitarbeiterList() enthält alle Mitarbeiter, die theoretisch eingesetzt werden könnten.
        for (Mitarbeiter mitarbeiter : plan.getMitarbeiterList()) {
            double zugewieseneStunden = zugewieseneStundenProMitarbeiter.getOrDefault(mitarbeiter, 0.0);
            double sollStunden = mitarbeiter.getWochenstunden();

            // Bestrafe die absolute Abweichung von den Soll-Wochenstunden
            softScore -= (int) (Math.abs(sollStunden - zugewieseneStunden) * 5); // 5 Punkte pro Stunde Abweichung
        }

        // Optional: Bestrafe Mitarbeiter, die gar nicht eingesetzt werden, falls die Sollstundenregel nicht ausreicht.
        // Diese Regel kann eine zusätzliche Anreiz bieten, wenn die Sollstunden-Regel noch nicht die gewünschte Verteilung bringt.
        for (Mitarbeiter mitarbeiter : plan.getMitarbeiterList()) {
            if (!eingesetzteMitarbeiter.contains(mitarbeiter)) {
                softScore -= 500; // Feste Strafe für jeden ungenutzten Mitarbeiter
            }
        }


        return HardSoftScore.of(hardScore, softScore);
    }
}