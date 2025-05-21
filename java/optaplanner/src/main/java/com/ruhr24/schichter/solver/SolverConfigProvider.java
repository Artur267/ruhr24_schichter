/*
package com.ruhr24.schichter.solver;

import com.ruhr24.schichter.domain.SchichtPlan;
import com.ruhr24.schichter.domain.Zuteilung;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.score.calculator.EasyScoreCalculator;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.optaplanner.core.api.solver.SolverFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;


import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Configuration
public class SolverConfigProvider {

    @Bean
    public SolverConfig solverConfig() {
        SolverConfig config = new SolverConfig();

        config.setSolutionClass(SchichtPlan.class);
        config.setEntityClassList(Collections.singletonList(Zuteilung.class));

        ScoreDirectorFactoryConfig scoreConfig = new ScoreDirectorFactoryConfig();
        scoreConfig.setEasyScoreCalculatorClass(SimpleScoreCalculator.class);
        config.setScoreDirectorFactoryConfig(scoreConfig);

        TerminationConfig terminationConfig = new TerminationConfig();
        terminationConfig.setSecondsSpentLimit(3L);
        config.setTerminationConfig(terminationConfig);

        return config;
    }


    @Bean
    public SolverFactory<SchichtPlan> solverFactory(SolverConfig solverConfig) {
        return SolverFactory.create(solverConfig);
    }

    @Bean
    public SolverManager<SchichtPlan, UUID> solverManager(SolverFactory<SchichtPlan> solverFactory) {
        return SolverManager.create(solverFactory);
    }
}
*/