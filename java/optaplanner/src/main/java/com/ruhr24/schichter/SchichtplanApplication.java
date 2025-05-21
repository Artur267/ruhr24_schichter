package com.ruhr24.schichter;

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.ruhr24.schichter.domain.SchichtPlan; // Importiere deine Solution-Klasse
import java.util.UUID; // Importiere UUID

@SpringBootApplication
public class SchichtplanApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchichtplanApplication.class, args);
    }

    /**
     * Konfiguriert den OptaPlanner SolverManager als Spring Bean.
     * Dies ist notwendig, damit der Solver die Konfiguration aus der solverConfig.xml lädt,
     * einschließlich der Termination-Limits und des Score-Calculators.
     * Ohne diese explizite Bean-Definition würde OptaPlanner eine Standardkonfiguration verwenden,
     * die oft keine Termination-Limits hat und deinen Score-Calculator nicht automatisch findet.
     *
     * @return Ein vorkonfigurierter SolverManager.
     */
    @Bean
    public SolverManager<SchichtPlan, UUID> solverManager() {
        // Erstellt eine SolverFactory, die die Konfiguration aus der solverConfig.xml liest.
        // Die Datei "solverConfig.xml" muss sich im src/main/resources-Verzeichnis befinden.
        SolverFactory<SchichtPlan> solverFactory = SolverFactory.createFromXmlResource(
                "solverConfig.xml"); 
        
        // Erstellt und gibt den SolverManager zurück, der diese Factory verwendet.
        return SolverManager.create(solverFactory);
    }
}