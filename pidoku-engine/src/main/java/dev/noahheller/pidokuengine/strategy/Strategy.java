package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.searching.SolverSearchState;

public interface Strategy {
    StrategyResult apply(SolverSearchState state);
}
