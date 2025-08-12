package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Abwesenheit; // NEU: Import für Abwesenheit
import com.ruhr24.schichter.domain.Arbeitsmuster;
import com.ruhr24.schichter.domain.Mitarbeiter;
import com.ruhr24.schichter.domain.Schicht;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;
import org.optaplanner.core.api.score.stream.ConstraintCollectors;

import java.util.Set;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    private static final long PENALTY_HARD = 1000L;
    private static final long PENALTY_HIGH = 200L;
    private static final long PENALTY_MEDIUM = 50L;
    private static final long PENALTY_LOW = 10L;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
                // ==== HARD CONSTRAINTS ====
                // NEU: Die wichtigste Regel zuerst
                mitarbeiterDarfNichtWaehrendAbwesenheitArbeiten(cf),

                wunschZuweisungIstFix(cf),
                einMusterProWocheProMitarbeiter(cf),
                keineZweiSportCvDsGleichzeitig(cf),
                cvdWochenendSequenz(cf), // Diese sind als Hard-Constraints sehr streng
                kernWochenendSequenz(cf), // was bedeutet, sie MÜSSEN erfüllt werden.

                // ==== SOFT CONSTRAINTS (Unsere Wünsche, nach Priorität geordnet) ====
                fairnessBeiWochenenden(cf),
                mitarbeiterPasstZuMuster(cf),
                rotiereSchichtTypen(cf),
                unbesetzteMusterBestrafen(cf)
        };
    }

    // =================================================================================
    // NEUE HARD CONSTRAINT
    // =================================================================================
    private Constraint mitarbeiterDarfNichtWaehrendAbwesenheitArbeiten(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
                // 1. Nimm jedes Arbeitsmuster, das einem Mitarbeiter zugewiesen ist
                .filter(arbeitsmuster -> arbeitsmuster.getMitarbeiter() != null)
                // 2. Kombiniere es mit jeder Abwesenheit, die für denselben Mitarbeiter gilt
                .join(Abwesenheit.class,
                        Joiners.equal(muster -> muster.getMitarbeiter().getId(), Abwesenheit::mitarbeiterId))
                // 3. Filtere nach den Kombinationen, bei denen eine Schicht des Musters auf einen Abwesenheitstag fällt.
                .filter((arbeitsmuster, abwesenheit) ->
                    // Prüfe für jede Schicht im Muster, ob ihr Datum im Abwesenheitszeitraum liegt
                    arbeitsmuster.getSchichten().stream().anyMatch(schicht ->
                        !schicht.getDatum().isBefore(abwesenheit.von()) &&
                        !schicht.getDatum().isAfter(abwesenheit.bis())
                    )
                )
                // 4. Bestrafe jede einzelne verletzende Schicht mit der Höchststrafe.
                .penalize(HardSoftLongScore.ONE_HARD,
                        (arbeitsmuster, abwesenheit) -> (int) arbeitsmuster.getSchichten().stream().filter(schicht ->
                                !schicht.getDatum().isBefore(abwesenheit.von()) &&
                                !schicht.getDatum().isAfter(abwesenheit.bis())
                        ).count()
                )
                .asConstraint("Mitarbeiter darf während Abwesenheit nicht arbeiten");
    }


    // =================================================================================
    // BESTEHENDE CONSTRAINTS (unverändert)
    // =================================================================================

    private Constraint einMusterProWocheProMitarbeiter(ConstraintFactory cf) {
        return cf.forEachUniquePair(Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(Arbeitsmuster::getWocheImJahr))
            .penalize(HardSoftLongScore.ofHard(PENALTY_HIGH))
            .asConstraint("Mitarbeiter hat zwei Muster in einer Woche");
    }

    private Constraint mitarbeiterPasstZuMuster(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(muster -> muster.getMitarbeiter() != null && !istMitarbeiterFuerMusterGeeignet(muster.getMitarbeiter(), muster))
            .penalize(HardSoftLongScore.ofSoft(PENALTY_HARD))
            .asConstraint("Mitarbeiter passt nicht zum Arbeitsmuster");
    }

    private Constraint wunschZuweisungIstFix(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(muster -> muster.getMusterTyp().startsWith("WUNSCH_") || muster.getMusterTyp().startsWith("PREFLIGHT_"))
            .filter(muster -> {
                if (muster.getMitarbeiter() == null) {
                    return true;
                }
                String[] teile = muster.getMusterTyp().split("_");
                String erwarteteMitarbeiterId = teile[teile.length - 1];
                return !muster.getMitarbeiter().getId().equals(erwarteteMitarbeiterId);
            })
            .penalize(HardSoftLongScore.ONE_HARD)
            .asConstraint("Wunsch-Muster muss korrekt zugewiesen sein");
    }

    private Constraint keineZweiSportCvDsGleichzeitig(ConstraintFactory factory) {
        Set<String> sportCvdMitarbeiterIds = Set.of("003", "014", "017");
        return factory
            .forEachUniquePair(Arbeitsmuster.class)
            .filter((muster1, muster2) -> {
                Mitarbeiter ma1 = muster1.getMitarbeiter();
                Mitarbeiter ma2 = muster2.getMitarbeiter();
                if (ma1 == null || ma2 == null || ma1.equals(ma2)) {
                    return false;
                }
                if (!sportCvdMitarbeiterIds.contains(ma1.getId()) || !sportCvdMitarbeiterIds.contains(ma2.getId())) {
                    return false;
                }
                return muster1.getSchichten().stream()
                    .filter(this::isCvdShift)
                    .anyMatch(schicht1 -> muster2.getSchichten().stream()
                        .filter(this::isCvdShift)
                        .anyMatch(schicht2 -> schicht1.getDatum().equals(schicht2.getDatum()))
                        );
                })
                .penalize(HardSoftLongScore.ONE_HARD)
                .asConstraint("Zwei Sport-CvDs am selben Tag");
    }

    private boolean isCvdShift(Schicht schicht) {
        return schicht.getSchichtTyp() != null && schicht.getSchichtTyp().toUpperCase().contains("CVD");
    }

    private Constraint cvdWochenendSequenz(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(weMuster -> weMuster.getMitarbeiter() != null && weMuster.getMusterTyp().startsWith("CVD_WE"))
            .ifNotExists(
                Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(muster -> muster.getWocheImJahr() + 1, Arbeitsmuster::getWocheImJahr),
                Joiners.filtering((weMuster, ausgleichsMuster) -> ausgleichsMuster.getMusterTyp().equals("CVD_AUSGLEICH_WOCHE"))
            )
            .penalize(HardSoftLongScore.ofHard(PENALTY_MEDIUM))
            .asConstraint("CvD sollte nach WE-Dienst eine Ausgleichswoche haben");
    }

    private Constraint kernWochenendSequenz(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(weMuster -> weMuster.getMitarbeiter() != null && weMuster.getMusterTyp().equals("REDAKTION_WE_WOCHE"))
            .ifNotExists(
                Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(weMuster -> weMuster.getWocheImJahr() + 1, Arbeitsmuster::getWocheImJahr),
                Joiners.filtering((weMuster, ausgleichsMuster) -> ausgleichsMuster.getMusterTyp().equals("REDAKTION_AUSGLEICH_WOCHE"))
            )
            .penalize(HardSoftLongScore.ofHard(PENALTY_MEDIUM))
            .asConstraint("KERN muss nach WE-Woche 1 eine Ausgleichs-Woche 2 haben");
    }

    private Constraint fairnessBeiWochenenden(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(muster -> muster.getMitarbeiter() != null && muster.getMusterTyp().contains("CVD_WE_"))
            .groupBy(Arbeitsmuster::getMitarbeiter, ConstraintCollectors.count())
            .penalize(HardSoftLongScore.ofSoft(PENALTY_HARD), (mitarbeiter, anzahl) -> anzahl * anzahl)
            .asConstraint("Faire Verteilung der Wochenend-Dienste");
    }

    private Constraint rotiereSchichtTypen(ConstraintFactory cf) {
        return cf.forEachUniquePair(Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(Arbeitsmuster::getMusterTyp),
                Joiners.filtering((m1, m2) -> Math.abs(m1.getWocheImJahr() - m2.getWocheImJahr()) == 1)
            )
            .penalize(HardSoftLongScore.ofSoft(PENALTY_LOW))
            .asConstraint("Rotiere die Mustertypen");
    }

    private Constraint unbesetzteMusterBestrafen(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(muster -> muster.getMitarbeiter() == null)
            .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
            .asConstraint("Unbesetztes Arbeitsmuster");
    }

    private boolean istMitarbeiterFuerMusterGeeignet(Mitarbeiter mitarbeiter, Arbeitsmuster muster) {
        String musterTyp = muster.getMusterTyp();
        if (musterTyp.startsWith("DANIELE_")) {
            return mitarbeiter.hasQualification("DANIELE_SONDERDIENST");
        }
        if (musterTyp.equals("LIBE_SONDERDIENST")) {
            return mitarbeiter.hasQualification("LIBE_SONDERDIENST");
        }
        if (musterTyp.equals("KEITER_SONDERDIENST")) {
            return mitarbeiter.hasQualification("KEITER_SONDERDIENST");
        }
        if (mitarbeiter.hasQualification("DANIELE_SONDERDIENST") || mitarbeiter.hasQualification("LIBE_SONDERDIENST") || mitarbeiter.hasQualification("KEITER_SONDERDIENST")) {
            return false;
        }
        if (musterTyp.equals("REDAKTION_WE_WOCHE_1") || musterTyp.equals("REDAKTION_AUSGLEICH_WOCHE_2")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
        if (musterTyp.equals("CVD_WE_FRUEH") || musterTyp.equals("CVD_WE_SPAET") || musterTyp.equals("CVD_AUSGLEICH_WOCHE")) {
            return mitarbeiter.hasQualification("CVD") || mitarbeiter.hasQualification("CVD_WE");
        }
        if (musterTyp.equals("CVD_KERN") || musterTyp.equals("CVD_FRUEH") || musterTyp.equals("CVD_SPAET")) {
            return mitarbeiter.hasQualification("CVD") || mitarbeiter.hasQualification("CVD_NOWE");
        }
        if (musterTyp.equals("ADMIN_40H")) {
            return mitarbeiter.hasQualification("NonOps");
        }
        if (musterTyp.equals("CVD_KERN") || musterTyp.equals("CVD_FRUEH") || musterTyp.equals("CVD_SPAET")) {
            return mitarbeiter.hasQualification("CVD_KERN");
        }
        if (musterTyp.equals("REDAKTION_40H")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
        if (musterTyp.equals("TZ_30H")) {
            return mitarbeiter.hasQualification("TZ_30H");
        }
        if (musterTyp.equals("TZ_20H")) {
            return mitarbeiter.hasQualification("TZ_20H");
        }
        if (musterTyp.equals("TZ_15H")) {
            return mitarbeiter.hasQualification("TZ_15H");
        }
        if (musterTyp.equals("TZ_10H")) {
            return mitarbeiter.hasQualification("TZ_10H");
        }
        if (musterTyp.equals("TZ_19H")) {
            return mitarbeiter.hasQualification("WerkstudentIn");
        }   
        return false;
    }
}
