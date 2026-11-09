package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyProfileTest {

    @Test
    void targetDefaultsToTheMiddleOfABoundedWindow() {
        DifficultyProfile hard = DifficultyProfile.hard();
        assertEquals((hard.minScore + hard.maxScore) / 2, hard.targetScore);
    }

    /** Regression: the midpoint of an open-ended window overflowed int, and later was ~1 billion. */
    @Test
    void openEndedWindowsDoNotAimAtInfinity() {
        DifficultyProfile open = new DifficultyProfile.Builder("Open").scoreRange(100, Integer.MAX_VALUE).build();
        assertEquals(100, open.targetScore);
        assertTrue(DifficultyProfile.fiendish().targetScore < 1000);
    }

    @Test
    void distanceToTargetIsSymmetric() {
        DifficultyProfile p = new DifficultyProfile.Builder("P").scoreRange(0, 100).targetScore(40).build();
        assertEquals(10, p.distanceToTarget(30));
        assertEquals(10, p.distanceToTarget(50));
        assertEquals(0, p.distanceToTarget(40));
    }

    @Test
    void premadeProfilesAreValid() {
        for (DifficultyProfile p : DifficultyProfile.premadeProfiles()) {
            assertTrue(p.targetScore >= p.minScore && p.targetScore <= p.maxScore, p.toString());
        }
    }

    @Test
    void rejectsSettingsNoPuzzleCouldMeet() {
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").scoreRange(50, 10).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").clueRange(40, 30).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").floor(Tier.EXPERT).ceiling(Tier.EASY).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").scoreRange(10, 20).targetScore(30).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").maxUses(StrategyEnum.X_WING, -1).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder(" ").build());
    }
}
