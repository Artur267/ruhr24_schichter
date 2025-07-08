package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.Arbeitsmuster;
import com.ruhr24.schichter.domain.Mitarbeiter;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;
import org.optaplanner.core.api.score.stream.ConstraintCollectors;

import java.time.LocalDate;

import org.optaplanner.core.api.domain.solution.ProblemFactProperty;

public class SchichtplanungConstraintProvider implements ConstraintProvider {

    // Wir definieren mehr Stufen für die Strafen
    private static final long PENALTY_HARD = 1000L;
    private static final long PENALTY_HIGH = 200L;
    private static final long PENALTY_MEDIUM = 50L;
    private static final long PENALTY_LOW = 10L;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
                // ==== HARD CONSTRAINTS ====
                einMusterProWocheProMitarbeiter(cf),

                // ==== SOFT CONSTRAINTS (Unsere Wünsche, nach Priorität geordnet) ====
                fairnessBeiWochenenden(cf),
                mitarbeiterPasstZuMuster(cf),
                cvdWochenendSequenz(cf),
                kernWochenendSequenz(cf),
                rotiereSchichtTypen(cf),
                unbesetzteMusterBestrafen(cf)
        };
    }

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


    private Constraint cvdWochenendSequenz(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            // Finde einen zugewiesenen CvD-Wochenend-Block
            .filter(weMuster -> weMuster.getMitarbeiter() != null && weMuster.getMusterTyp().startsWith("CVD_WE"))
            
            // Prüfe, ob in der Folgewoche KEIN passendes Ausgleichsmuster existiert
            .ifNotExists(
                Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(muster -> muster.getWocheImJahr() + 1, Arbeitsmuster::getWocheImJahr),
                Joiners.filtering((weMuster, ausgleichsMuster) -> ausgleichsMuster.getMusterTyp().equals("CVD_AUSGLEICH_WOCHE"))
            )
            // Bestrafe dies als hohe Soft-Constraint, nicht mehr als Hard-Constraint
            .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
            .asConstraint("CvD sollte nach WE-Dienst eine Ausgleichswoche haben");
    }

    private Constraint kernWochenendSequenz(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(weMuster -> weMuster.getMitarbeiter() != null && weMuster.getMusterTyp().equals("REDAKTION_WE_WOCHE_1"))
            .join(LocalDate.class) 
            .filter((weMuster, endDatum) -> {
                LocalDate startDerFolgeWoche = weMuster.getSchichten().get(0).getDatum().plusWeeks(1);
                return !startDerFolgeWoche.isAfter(endDatum);
            })
            .ifNotExists(Arbeitsmuster.class,
                // KORREKTUR: Die Joiner müssen jetzt mit dem (muster1, endDatum)-Paar arbeiten
                Joiners.equal((muster1, endDatum) -> muster1.getMitarbeiter(), Arbeitsmuster::getMitarbeiter),
                Joiners.equal((muster1, endDatum) -> muster1.getWocheImJahr() + 1, Arbeitsmuster::getWocheImJahr),
                Joiners.filtering((muster1, endDatum, muster2) -> muster2.getMusterTyp().equals("REDAKTION_AUSGLEICH_WOCHE_2"))
            )
            .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
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
                // Finde Paare vom selben Mitarbeiter...
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                // ... mit demselben Muster-Typ (z.B. zweimal hintereinander "CVD_FRUEH") ...
                Joiners.equal(Arbeitsmuster::getMusterTyp),
                // ... die in aufeinanderfolgenden Wochen stattfinden.
                Joiners.filtering((m1, m2) -> Math.abs(m1.getWocheImJahr() - m2.getWocheImJahr()) == 1)
            )
            .penalize(HardSoftLongScore.ofSoft(PENALTY_LOW)) // Eine kleine Strafe als "Anstupser"
            .asConstraint("Rotiere die Mustertypen");
    }

    private Constraint unbesetzteMusterBestrafen(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            .filter(muster -> muster.getMitarbeiter() == null)
            .penalize(HardSoftLongScore.ofSoft(PENALTY_MEDIUM))
            .asConstraint("Unbesetztes Arbeitsmuster");
    }



    private Constraint abweichungVonSollStundenMinimieren(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
                // 1. Nimm nur die Muster, die einem Mitarbeiter zugewiesen sind
                .filter(muster -> muster.getMitarbeiter() != null)
                // 2. Gruppiere sie nach diesem Mitarbeiter und summiere die Stunden der zugewiesenen Muster
                .groupBy(Arbeitsmuster::getMitarbeiter, ConstraintCollectors.sum(Arbeitsmuster::getWochenstunden))
                // 3. Bestrafe die Abweichung von der vertraglichen Soll-Stundenzahl
                .penalizeLong(HardSoftLongScore.ofSoft(PENALTY_HIGH), // Hohe Strafe, da dies das Hauptziel ist
                        (mitarbeiter, tatsaechlicheStunden) -> {
                            // Zielstunden für den gesamten Planungszeitraum (Annahme: 4 Wochen)
                            long sollStunden = (long) mitarbeiter.getWochenstunden() * 4;
                            long abweichung = Math.abs(tatsaechlicheStunden - sollStunden);
                            // Quadratische Strafe, damit große Abweichungen sehr teuer werden
                            return abweichung * abweichung;
                        })
                .asConstraint("Abweichung von monatlichen Soll-Stunden");
    }

    private Constraint danieleWochenendSequenz(ConstraintFactory cf) {
        return cf.forEach(Arbeitsmuster.class)
            // Finde eine zugewiesene Woche 1
            .filter(muster1 -> muster1.getMitarbeiter() != null && muster1.getMusterTyp().equals("DANIELE_WE_WOCHE_1"))
            // Prüfe, ob es für denselben Mitarbeiter in der Folgewoche KEINE Woche 2 gibt
            .ifNotExists(Arbeitsmuster.class,
                Joiners.equal(Arbeitsmuster::getMitarbeiter),
                Joiners.equal(muster -> muster.getWocheImJahr() + 1, Arbeitsmuster::getWocheImJahr),
                // Die Folgewoche MUSS vom Typ DANIELE_WE_WOCHE_2 sein
                Joiners.filtering((muster1, muster2) -> muster2.getMusterTyp().equals("DANIELE_WE_WOCHE_2"))
            )
            .penalize(HardSoftLongScore.ofHard(PENALTY_HIGH))
            .asConstraint("Daniele muss nach WE-Woche 1 eine WE-Woche 2 haben");
    }

    // GEÄNDERT: Die harte Stundenprüfung ist raus!
    private boolean istMitarbeiterFuerMusterGeeignet(Mitarbeiter mitarbeiter, Arbeitsmuster muster) {
        String musterTyp = muster.getMusterTyp();
        

        if (musterTyp.startsWith("DANIELE_")) {
            return mitarbeiter.hasQualification("DANIELE_SONDERDIENST");
        }
        if (musterTyp.equals("LIBE_SONDERDIENST")) {
            return mitarbeiter.hasQualification("LIBE_SONDERDIENST");
        }
        if (mitarbeiter.hasQualification("DANIELE_SONDERDIENST") || mitarbeiter.hasQualification("LIBE_SONDERDIENST")) {
            return false;
        }


        if (musterTyp.equals("REDAKTION_WE_WOCHE_1") || musterTyp.equals("REDAKTION_AUSGLEICH_WOCHE_2")) {
            // Jeder normale Redakteur mit 40h darf diese Rotationen machen
            return mitarbeiter.hasQualification("REDAKTION");
        }
        if (musterTyp.equals("CVD_WE_FRUEH") || musterTyp.equals("CVD_WE_SPAET") || musterTyp.equals("CVD_AUSGLEICH_WOCHE")) {
            // Nur CvDs, die für den Wochenenddienst qualifiziert sind
            return mitarbeiter.hasQualification("CVD_WE_FRUEH");
        }

        if (mitarbeiter.getWochenstunden() != muster.getWochenstunden()) {
            return false;
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

        if (musterTyp.equals("REDAKTION_40H")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
        /*
        if (mitarbeiter.getWochenstunden() != muster.getWochenstunden()) {
            return false;
        }
         */
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


        /*
        if (musterTyp.equals("TZ_15H")) {
            return mitarbeiter.hasQualification("TZ_15H");
        }   
        if (musterTyp.equals("TZ_19H")) {
            return mitarbeiter.hasQualification("WerkstudentIn");
        }   


        if (musterTyp.equals("ADMIN_40H")) {
            return mitarbeiter.hasQualification("NonOps");
        }
        if (musterTyp.equals("CVD_WE_SPAET")) {
            return mitarbeiter.hasQualification("CVD_WE_SPAET");
        }
        if (musterTyp.equals("CVD_WE_FRUEH")) {
            return mitarbeiter.hasQualification("CVD_WE_FRUEH");
        }
        if (musterTyp.equals("CVD_SPAET")) {
            return mitarbeiter.hasQualification("CVD_SPAET");
        }
        if (musterTyp.equals("CVD_FRUEH")) {
            return mitarbeiter.hasQualification("CVD_FRUEH");
        }
        
        if (musterTyp.equals("KERNDIENST_AUSGLEICH_REDAKTION")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
        
        if (musterTyp.equals("KERNDIENST_WE_REDAKTION")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
             
        if (musterTyp.equals("REDAKTION_40H")) {
            return mitarbeiter.hasQualification("REDAKTION");
        }
        if (mitarbeiter.getWochenstunden() != muster.getWochenstunden()) {
            return false;
        }
        if (musterTyp.startsWith("TZ_")) { // Allgemeine Regel für alle Teilzeit-Muster
            return true; // Stunden wurden ja schon geprüft
        }
            */
        return false;
    }
}

