package dev.noahheller.pidokuengine.explain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver.NextMove;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Unit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the frontend's tutorial puzzle ({@code pidoku-frontend/src/tutorial/tutorial.json}): a
 * nearly full 9×9 board, the guided steps the tutorial walks through, then the rest of the solve
 * as the solver would make it, every step worded by {@link MoveExplainer}.
 *
 * <p>Run {@code ./gradlew :pidoku-engine:exportTutorial} after changing the wording or the board.
 * {@code TutorialExportTest} fails while the committed file is out of date, so the tutorial
 * always says what the engine would.
 *
 * <p>The guided steps are checked here: each has to be a single the engine finds on the board as
 * it is by then, in the unit named, or the export fails.
 */
public final class TutorialExport {

    /** A real engine puzzle's solution (the bundled Medium puzzle). */
    static final String SOLUTION =
            "321419565614515932595326114415931526269541153153652419931254651542165391156193245";

    /** The cells left empty (row, column; 0-based). Everything else is a given. */
    private static final int[][] EMPTY_CELLS = {
            {2, 7}, {2, 8}, {3, 1}, {3, 6}, {3, 8}, {4, 2}, {4, 7}, {5, 0}, {7, 8},
    };

    /** A step the tutorial walks the player through, and the unit its explanation must be about. */
    private record Guided(int row, int col, StrategyEnum strategy, Unit unit) {
    }

    private static final List<Guided> GUIDED = List.of(
            // A plain single: the middle-left box needs a 9, and only one cell can take it.
            new Guided(4, 2, StrategyEnum.HIDDEN_SINGLE_BOX, Unit.box(3)),
            // The twist: row 4 already has one 5 and needs its second.
            new Guided(3, 6, StrategyEnum.HIDDEN_SINGLE_LINE, Unit.row(3)),
            // After the notes lesson: column 8 needs its second 1, which fills row 3's 1s and
            // clears the 1 the player pencilled in beside it.
            new Guided(2, 7, StrategyEnum.HIDDEN_SINGLE_LINE, Unit.column(7)));

    private TutorialExport() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: TutorialExport <output.json>");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, json(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The tutorial file's contents. */
    public static String json() {
        PidokuRules rules = new PidokuRules(9);
        int size = rules.getSize();
        int[] solution = SOLUTION.chars().map(c -> c - '0').toArray();
        int[][] grid = new int[size][size];
        for (int i = 0; i < size * size; i++) grid[i / size][i % size] = solution[i];
        for (int[] cell : EMPTY_CELLS) grid[cell[0]][cell[1]] = PidokuRules.EMPTY;
        int[] givens = new int[size * size];
        for (int i = 0; i < size * size; i++) givens[i] = grid[i / size][i % size];

        SolverSearchState state = SolverSearchState.from(rules, grid);
        if (!state.isValid()) throw new IllegalStateException("The tutorial board breaks the rules");

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Guided guided : GUIDED) {
            BoardPosition cell = new BoardPosition(guided.row(), guided.col());
            int digit = solution[guided.row() * size + guided.col()];
            Move move = guided.strategy().getStrategy().findAll(state).stream()
                    .filter(m -> m.isPlacement() && m.getPosition().equals(cell) && m.getValue() == digit)
                    .filter(m -> m.getReason().units().getFirst().equals(guided.unit()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Guided step " + digit + " at " + cell + " isn't a " + guided.strategy() + " in " + guided.unit()));
            steps.add(step(MoveExplainer.explain(guided.strategy(), move, state), size, true));
            move.applyTo(state);
        }

        while (!SearchUtils.isSolved(state)) {
            NextMove next = HumanPidokuSolver.nextMove(state)
                    .orElseThrow(() -> new IllegalStateException("The tutorial board can't be finished with logic"));
            steps.add(step(next.explain(state), size, false));
            next.move().applyTo(state);
        }
        for (int i = 0; i < size * size; i++) {
            if (state.getValue(new BoardPosition(i / size, i % size)) != solution[i]) {
                throw new IllegalStateException("The solver finished the tutorial board differently at cell " + i);
            }
        }

        Map<String, Object> file = new LinkedHashMap<>();
        file.put("comment", "Generated by ./gradlew :pidoku-engine:exportTutorial (TutorialExport). Don't edit by hand.");
        file.put("size", size);
        file.put("givens", givens);
        file.put("solution", solution);
        file.put("steps", steps);
        try {
            return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(file)
                    .replace("\r\n", "\n") + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An explanation as the frontend reads it: cells as row-major indices. */
    private static Map<String, Object> step(Explanation explanation, int size, boolean guided) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("guided", guided);
        step.put("strategy", explanation.strategy().name());
        step.put("cell", explanation.cell() == null ? null : index(explanation.cell(), size));
        step.put("digit", explanation.digit());
        step.put("units", explanation.units().stream()
                .map(u -> ordered("type", u.type().name().toLowerCase(), "index", u.index())).toList());
        step.put("pattern", explanation.pattern().stream().map(p -> index(p, size)).toList());
        step.put("blockers", explanation.blockers().stream().map(p -> index(p, size)).toList());
        step.put("eliminations", explanation.eliminations().stream()
                .map(c -> ordered("cell", index(c.position(), size), "digit", c.value())).toList());
        step.put("text", explanation.text());
        return step;
    }

    /**
     * A two-key object whose keys are written in this order. Not {@code Map.of}: its iteration
     * order changes from one JVM run to the next, so the file would too.
     */
    private static Map<String, Object> ordered(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static int index(BoardPosition position, int size) {
        return position.getRow() * size + position.getCol();
    }
}
