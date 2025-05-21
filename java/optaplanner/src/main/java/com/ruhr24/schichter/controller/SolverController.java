package com.ruhr24.schichter.controller;

import com.ruhr24.schichter.domain.SchichtPlan;
import org.optaplanner.core.api.solver.SolverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class SolverController {

    @Autowired
    private SolverManager<SchichtPlan, UUID> solverManager;

    @PostMapping("/solve")
    public SchichtPlan solve(@RequestBody SchichtPlan unsolved) throws ExecutionException, InterruptedException {
        return solverManager.solve(UUID.randomUUID(), unsolved).getFinalBestSolution();
    }
}
