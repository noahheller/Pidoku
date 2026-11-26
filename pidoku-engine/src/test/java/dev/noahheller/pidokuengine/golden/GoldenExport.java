package dev.noahheller.pidokuengine.golden;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuBuilder;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver.NextMove;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.noahheller.pidokuengine.PidokuTestSupport.fullSolution;
import static dev.noahheller.pidokuengine.PidokuTestSupport.punchHoles;
import static dev.noahheller.pidokuengine.PidokuTestSupport.rules;

/**
 * The engine's outputs on fixed inputs, saved so a refactor can prove it changed nothing.
 * {@link GoldenTest} recomputes them and compares. When a change is meant to alter the outputs
 * (a new strategy, a scoring change), regenerate with {@code ./gradlew :pidoku-engine:updateGolden}.
 *
 * <ul>
 *   <li>{@code generation.json}: puzzles generated from fixed seeds for every premade profile,
 *       with their score and every step of their solve.</li>
 *   <li>{@code strategies.txt}: one hash per strategy of everything its {@code findAll} returns
 *       (moves, elimination order, reasons) at every step of solving seeded boards, plus hashes of
 *       the solver's chosen moves and their explanations (what hints show).</li>
 * </ul>
 */
public final class GoldenExport {

    /** Gradle runs the engine's tests and JavaExec tasks from pidoku-engine/. */
    static final Path DIR = Path.of("src/test/resources/golden");
    static final Path GENERATION = DIR.resolve("generation.json");
    static final Path STRATEGIES = DIR.resolve("strategies.txt");

    static final long[] SEEDS = {1, 2};
    static final int BOARDS = 200;
    /** Long enough that a slow machine never times out, which would change the result. */
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private GoldenExport() {
    }

    /** One generated puzzle. {@code puzzle} is the 81 cells in reading order, 0 for a blank. */
    public record GenerationCase(String profile, long seed, String puzzle, int score, String hardest,
                                 List<String> steps) {
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DIR);
        Files.writeString(GENERATION, generationJson(generationCases()), StandardCharsets.UTF_8);
        Files.writeString(STRATEGIES, strategiesText(strategyHashes()), StandardCharsets.UTF_8);
        System.out.println("Wrote " + GENERATION.toAbsolutePath() + " and " + STRATEGIES.toAbsolutePath());
    }

    static List<GenerationCase> generationCases() {
        PidokuRules rules = rules();
        PidokuBuilder builder = PidokuBuilder.standard(rules);
        List<GenerationCase> cases = new ArrayList<>();
        for (DifficultyProfile profile : DifficultyProfile.premadeProfiles()) {
            for (long seed : SEEDS) {
                PidokuPuzzle puzzle = builder.generate(profile, seed, TIMEOUT);
                if (puzzle == null) {
                    cases.add(new GenerationCase(profile.name, seed, null, 0, null, List.of()));
                    continue;
                }
                SolveTrace trace = puzzle.getSolveTrace();
                cases.add(new GenerationCase(profile.name, seed, cells(puzzle.getPuzzle()), trace.getTotalScore(),
                        String.valueOf(trace.getHardestStrategy()), trace.getSteps().stream().map(GoldenExport::step).toList()));
            }
        }
        return cases;
    }

    static String generationJson(List<GenerationCase> cases) throws IOException {
        return JSON.writeValueAsString(cases).replace("\r\n", "\n") + "\n";
    }

    static List<GenerationCase> readGeneration(String json) throws IOException {
        return JSON.readValue(json, new TypeReference<>() {});
    }

    private static String cells(int[][] grid) {
        StringBuilder sb = new StringBuilder();
        for (int[] row : grid) for (int v : row) sb.append(v);
        return sb.toString();
    }

    /** e.g. {@code HIDDEN_SINGLE_BOX r4c2=2 cost1.0 opt7}, or {@code X_WING -3 cost18.0 opt0}. */
    private static String step(SolveTrace.Step step) {
        String what = step.isPlacement()
                ? "r" + step.getPosition().getRow() + "c" + step.getPosition().getCol() + "=" + step.getValue()
                : "-" + step.getEliminationCount();
        return step.getStrategy() + " " + what + " cost" + step.getCost() + " opt" + step.getOptions();
    }

    /** Keys: every strategy's name, then NEXT_MOVE and EXPLANATION. */
    static Map<String, String> strategyHashes() {
        PidokuRules rules = rules();
        Map<StrategyEnum, MessageDigest> perStrategy = new EnumMap<>(StrategyEnum.class);
        for (StrategyEnum s : StrategyEnum.values()) perStrategy.put(s, sha256());
        MessageDigest nextMoves = sha256();
        MessageDigest explanations = sha256();

        for (long seed = 0; seed < BOARDS; seed++) {
            int[][] solution = fullSolution(seed);
            int holes = 45 + (int) (seed % 20);
            SolverSearchState state = SolverSearchState.from(rules, punchHoles(solution, holes, seed));
            int step = 0;
            while (!SearchUtils.isSolved(state)) {
                String at = "board " + seed + " step " + step + ": ";
                for (StrategyEnum s : StrategyEnum.values()) {
                    StringBuilder sb = new StringBuilder(at);
                    for (Move move : s.getStrategy().findAll(state)) {
                        sb.append(move).append(" because ").append(move.getReason()).append('\n');
                    }
                    update(perStrategy.get(s), sb.toString());
                }
                Optional<NextMove> next = HumanPidokuSolver.nextMove(state);
                if (next.isEmpty()) {
                    update(nextMoves, at + "stuck\n");
                    break;
                }
                NextMove move = next.get();
                update(nextMoves, at + move.strategy() + " " + move.move() + " " + move.cost() + " " + move.options() + "\n");
                update(explanations, at + move.explain(state) + "\n");
                move.move().applyTo(state);
                step++;
            }
        }

        Map<String, String> hashes = new LinkedHashMap<>();
        perStrategy.forEach((s, d) -> hashes.put(s.name(), hex(d)));
        hashes.put("NEXT_MOVE", hex(nextMoves));
        hashes.put("EXPLANATION", hex(explanations));
        return hashes;
    }

    static String strategiesText(Map<String, String> hashes) {
        StringBuilder sb = new StringBuilder();
        hashes.forEach((name, hash) -> sb.append(name).append(' ').append(hash).append('\n'));
        return sb.toString();
    }

    static Map<String, String> readStrategies(String text) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.trim().split(" ");
            hashes.put(parts[0], parts[1]);
        }
        return hashes;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }
}
