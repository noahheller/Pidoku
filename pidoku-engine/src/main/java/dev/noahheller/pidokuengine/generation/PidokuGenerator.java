package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuPuzzle;

import java.util.random.RandomGenerator;

public interface PidokuGenerator {

    PidokuPuzzle buildPuzzle(int[][] solution, DifficultyProfile difficultyProfile, RandomGenerator random);
}
