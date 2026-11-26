package dev.noahheller.pidokuengine.golden;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The engine still produces exactly the saved outputs (see {@link GoldenExport}). A failure means
 * generation, scoring, a strategy or an explanation changed. If that was the point of the change,
 * regenerate the files; if it was meant to be a pure refactor, it isn't one.
 */
class GoldenTest {

    private static final String UPDATE = "\nOnly if the change is intended: run ./gradlew :pidoku-engine:updateGolden"
            + " (and bump the engine version if generation or scoring changed).";

    @Test
    void generationMatchesTheGoldenFile() throws IOException {
        assertTrue(Files.exists(GoldenExport.GENERATION), "Missing " + GoldenExport.GENERATION.toAbsolutePath() + UPDATE);
        List<GoldenExport.GenerationCase> expected =
                GoldenExport.readGeneration(Files.readString(GoldenExport.GENERATION, StandardCharsets.UTF_8));
        List<GoldenExport.GenerationCase> actual = GoldenExport.generationCases();

        assertEquals(expected.size(), actual.size(), "number of generated cases" + UPDATE);
        List<String> differences = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            GoldenExport.GenerationCase e = expected.get(i);
            GoldenExport.GenerationCase a = actual.get(i);
            String label = e.profile() + " seed " + e.seed() + ": ";
            if (!e.equals(a)) differences.add(label + firstDifference(e, a));
        }
        if (!differences.isEmpty()) fail(String.join("\n", differences) + UPDATE);
    }

    @Test
    void strategiesMatchTheGoldenFile() throws IOException {
        assertTrue(Files.exists(GoldenExport.STRATEGIES), "Missing " + GoldenExport.STRATEGIES.toAbsolutePath() + UPDATE);
        Map<String, String> expected =
                GoldenExport.readStrategies(Files.readString(GoldenExport.STRATEGIES, StandardCharsets.UTF_8));
        Map<String, String> actual = GoldenExport.strategyHashes();

        List<String> differences = new ArrayList<>();
        for (String key : actual.keySet()) {
            if (!actual.get(key).equals(expected.get(key))) differences.add(key + " differs");
        }
        for (String key : expected.keySet()) {
            if (!actual.containsKey(key)) differences.add(key + " is no longer produced");
        }
        if (!differences.isEmpty()) fail(String.join("\n", differences) + UPDATE);
    }

    private static String firstDifference(GoldenExport.GenerationCase e, GoldenExport.GenerationCase a) {
        if (!java.util.Objects.equals(e.puzzle(), a.puzzle())) return "puzzle was " + e.puzzle() + ", now " + a.puzzle();
        if (e.score() != a.score()) return "score was " + e.score() + ", now " + a.score();
        if (!java.util.Objects.equals(e.hardest(), a.hardest())) return "hardest was " + e.hardest() + ", now " + a.hardest();
        int n = Math.min(e.steps().size(), a.steps().size());
        for (int i = 0; i < n; i++) {
            if (!e.steps().get(i).equals(a.steps().get(i))) {
                return "step " + i + " was " + e.steps().get(i) + ", now " + a.steps().get(i);
            }
        }
        return "had " + e.steps().size() + " steps, now " + a.steps().size();
    }
}
