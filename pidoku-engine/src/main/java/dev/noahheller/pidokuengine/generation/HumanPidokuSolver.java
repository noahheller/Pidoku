package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.StrategyResult;

public class HumanPidokuSolver implements PidokuSolver{
    private final PidokuRules rules;

    public HumanPidokuSolver(PidokuRules rules) {
        this.rules = rules;
    }

    public PidokuPuzzle solve(int[][] puzzle) {
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        SolveTrace trace = new SolveTrace();

        while (!SearchUtils.isSolved(state)) {
            StrategyResult result = tryAllStrategies(state, trace);

            if (!result.madeProgress()) {
                trace.requiresGuessing = true;
                return new PidokuPuzzle(puzzle, trace);
            }
        }

        trace.solved = true;
        return new PidokuPuzzle(puzzle, trace);
    }

    private StrategyResult tryAllStrategies(SolverSearchState state, SolveTrace trace) {
        StrategyEnum[] values = StrategyEnum.values();
        for (StrategyEnum strat: values){
            StrategyResult result = strat.getStrategy().apply(state);
            if (result.madeProgress()){
                trace.record(strat, result);
                return result;
            }
        }
        return StrategyResult.FAILED;
    }

}
