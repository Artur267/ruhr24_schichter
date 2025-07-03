package com.ruhr24.schichter;

import com.ruhr24.schichter.domain.SchichtPlan;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.UUID;

@SpringBootApplication
public class SchichtplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchichtplanApplication.class, args);
    }

    /**
     * KORREKTUR: Wir erstellen die SolverFactory als eigene, zentrale Bean.
     * Auf diese eine Instanz können jetzt alle anderen Beans zugreifen.
     */
    @Bean
    public SolverFactory<SchichtPlan> solverFactory() {
        return SolverFactory.createFromXmlResource("solverConfig.xml");
    }

    /**
     * KORREKTUR: Diese Methode nimmt jetzt die zentrale SolverFactory-Bean als Parameter.
     */
    @Bean
    public SolverManager<SchichtPlan, UUID> solverManager(SolverFactory<SchichtPlan> solverFactory) {
        return SolverManager.create(solverFactory);
    }

    /**
     * KORREKTUR: Diese Methode nimmt ebenfalls die zentrale SolverFactory-Bean als Parameter.
     */
    @Bean
    public SolutionManager<SchichtPlan, HardSoftLongScore> solutionManager(SolverFactory<SchichtPlan> solverFactory) {
        return SolutionManager.create(solverFactory);
    }
}