package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.DifficultyProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanPidokuGeneratorTest {

    /**
     * Regression: (minScore + maxScore) / 2 overflowed in int for Fiendish
     * (maxScore = Integer.MAX_VALUE), giving a negative target that made
     * swap refinement stop on its first iteration.
     */
    @Test
    void midpointDoesNotOverflowForOpenEndedProfile() {
        DifficultyProfile fiendish = DifficultyProfile.fiendish();
        long midpoint = HumanPidokuGenerator.midpointScore(fiendish);

        assertTrue(midpoint > fiendish.minScore, "midpoint was " + midpoint);
        assertEquals((fiendish.minScore + (long) Integer.MAX_VALUE) / 2, midpoint);
    }

    @Test
    void midpointOfBoundedProfile() {
        DifficultyProfile hard = DifficultyProfile.hard();
        assertEquals((hard.minScore + hard.maxScore) / 2, HumanPidokuGenerator.midpointScore(hard));
    }
}
