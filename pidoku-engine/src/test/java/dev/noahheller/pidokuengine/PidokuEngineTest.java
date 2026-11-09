package dev.noahheller.pidokuengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PidokuEngineTest {
    @Test
    void versionComesFromTheBuild() {
        assertTrue(PidokuEngine.VERSION.matches("\\d+\\.\\d+\\.\\d+.*"), "unexpected version: " + PidokuEngine.VERSION);
    }

    @Test
    void puzzlesCarryTheEngineVersion() {
        PidokuPuzzle puzzle = new PidokuPuzzle(new int[9][9], new int[9][9]);
        assertEquals(PidokuEngine.VERSION, puzzle.getEngineVersion());
    }
}
