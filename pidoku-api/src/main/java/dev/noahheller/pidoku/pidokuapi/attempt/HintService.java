package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.Grids;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.explain.Explanation;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver.NextMove;
import dev.noahheller.pidokuengine.searching.DifficultyScorer;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Hints (milestone 6): the move the human solver would make next on the player's board, revealed a
 * step at a time and charged to the attempt (see {@link Penalties}). The wording and highlighting
 * come from the engine's explain layer, the same as the tutorial's.
 *
 * <p>The board's digits are all the solver sees. The player's notes are only used to skip an
 * elimination they've clearly already made (every cell it clears has notes without that digit),
 * and eliminations from hints already shown in full are remembered on the attempt. Otherwise an
 * X-Wing, which places no digit, would be hinted again and again.
 *
 * <p>An elimination is shown in full in one tap, and the placement it leads to picks up at step 2
 * (see {@link Attempt#revealHint}), so the player isn't charged a whole hint for each.
 */
@Service
public class HintService {

    /** More than enough eliminations in a row for any board; only a guard against a loop. */
    private static final int MAX_SKIPPED = 500;

    private final AttemptRepository attempts;

    public HintService(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    /**
     * Reveals the next hint step for the board and records it on the attempt.
     *
     * @param values the board's digits, flat and row-major, 0 for empty (givens included)
     * @param notes  each cell's notes as a bitmask (bit {@code 1 << d} for digit d), or null
     * @throws AttemptNotFoundException if the player has no attempt with the id
     * @throws InvalidProgressException if the board doesn't fit the puzzle
     * @throws WrongDigitsException     if the board has digits that aren't the solution's
     * @throws NoHintException          if the attempt is complete, the board is full, or the
     *                                  solver finds no next step
     */
    @Transactional
    public HintResponse hint(Player player, long attemptId, int[] values, int[] notes) {
        Attempt attempt = attempts.findForUpdate(attemptId, player)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
        if (attempt.isCompleted()) {
            throw new NoHintException("This puzzle is already solved.");
        }
        Puzzle puzzle = attempt.getPuzzle();
        int size = puzzle.getSize();
        int[] givens = Grids.toCells(puzzle.getGivens());
        int[] solution = Grids.toCells(puzzle.getSolution());
        checkFits(values, notes, givens);

        List<Integer> wrong = new ArrayList<>();
        boolean full = true;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) full = false;
            else if (values[i] != solution[i]) wrong.add(i);
        }
        if (!wrong.isEmpty()) {
            throw new WrongDigitsException(wrong);
        }
        if (full) {
            throw new NoHintException("The board is already full.");
        }

        SolverSearchState state = SolverSearchState.from(new PidokuRules(size), Grids.toGrid(values, size));
        for (int[] candidate : attempt.shownEliminations()) {
            state.eliminate(candidate[0] / size, candidate[0] % size, candidate[1]);
        }
        // Stay with the move being hinted while it still applies, even if a correct digit
        // elsewhere made another move easier: the player is part-way through this one.
        NextMove next = sameMoveAsLastTime(state, attempt.getLastHint(), notes, size)
                .orElseGet(() -> nextUnmadeMove(state, notes, size));
        Explanation explanation = next.explain(state);

        List<int[]> eliminations = next.move().isPlacement() ? List.of()
                : next.move().getEliminations().stream()
                        .map(c -> new int[]{index(c.position(), size), c.value()})
                        .toList();
        int step = attempt.revealHint(key(next, size), eliminations, isElimination(attempt.getLastHint()));
        return response(explanation, step, size, attempt);
    }

    /** The move hinted last time, if it can still be made on this board and the notes don't show it made. */
    private static Optional<NextMove> sameMoveAsLastTime(SolverSearchState state, LastHint last, int[] notes, int size) {
        if (last == null) return Optional.empty();
        StrategyEnum strategy;
        try {
            strategy = StrategyEnum.valueOf(last.key().substring(0, last.key().indexOf(':')));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return Optional.empty();
        }
        return strategy.getStrategy().findAll(state).stream()
                .map(move -> new NextMove(strategy, move, DifficultyScorer.moveCost(strategy, move, state.getRules()), 0))
                .filter(candidate -> key(candidate, size).equals(last.key()))
                .filter(candidate -> candidate.move().isPlacement() || !madeInNotes(candidate.move(), notes, size))
                .findFirst();
    }

    /** The solver's next move, past any eliminations the player's notes show they've made. */
    private static NextMove nextUnmadeMove(SolverSearchState state, int[] notes, int size) {
        for (int skipped = 0; skipped <= MAX_SKIPPED; skipped++) {
            NextMove next = HumanPidokuSolver.nextMove(state)
                    .orElseThrow(() -> new NoHintException("No hint is available for this board."));
            if (next.move().isPlacement() || !madeInNotes(next.move(), notes, size)) {
                return next;
            }
            next.move().applyTo(state);
        }
        throw new NoHintException("No hint is available for this board.");
    }

    /** Every cell the elimination clears has notes, and none of them has the digit cleared. */
    private static boolean madeInNotes(Move move, int[] notes, int size) {
        if (notes == null) return false;
        for (Move.Candidate candidate : move.getEliminations()) {
            int cellNotes = notes[index(candidate.position(), size)];
            if (cellNotes == 0 || (cellNotes & (1 << candidate.value())) != 0) return false;
        }
        return true;
    }

    /** The hint was for a move that only clears candidates (its key has no {@code cell=digit}). */
    private static boolean isElimination(LastHint last) {
        return last != null && !last.key().contains("=");
    }

    /** Identifies a move, so asking again for the same one reveals its next step. */
    private static String key(NextMove next, int size) {
        Move move = next.move();
        String what = move.isPlacement()
                ? index(move.getPosition(), size) + "=" + move.getValue()
                : move.getEliminations().stream()
                        .map(c -> index(c.position(), size) + "-" + c.value())
                        .sorted()
                        .collect(Collectors.joining(","));
        return next.strategy().name() + ":" + what;
    }

    private static HintResponse response(Explanation explanation, int step, int size, Attempt attempt) {
        List<HintResponse.UnitRef> units = explanation.units().stream()
                .map(u -> new HintResponse.UnitRef(u.type().name().toLowerCase(), u.index()))
                .toList();
        boolean what = step >= 2;
        boolean answer = step >= 3;
        // For a single the pattern is the target itself, so it waits for the answer step.
        List<BoardPosition> pattern = answer ? explanation.pattern()
                : explanation.pattern().stream().filter(p -> !p.equals(explanation.cell())).toList();
        return new HintResponse(
                step,
                units,
                what ? explanation.strategy().name() : null,
                what ? indices(pattern, size) : null,
                what ? indices(explanation.blockers(), size) : null,
                answer && explanation.cell() != null ? index(explanation.cell(), size) : null,
                answer && explanation.isPlacement() ? explanation.digit() : null,
                answer ? explanation.eliminations().stream()
                        .sorted(Comparator.comparingInt((Move.Candidate c) -> index(c.position(), size))
                                .thenComparingInt(Move.Candidate::value))
                        .map(c -> new HintResponse.CandidateRef(index(c.position(), size), c.value()))
                        .toList() : null,
                answer ? explanation.text() : null,
                attempt.getHintsUsed(),
                attempt.getHintPenaltyMs());
    }

    /** The board has the puzzle's size, keeps its givens, and holds only digits and note bits. */
    private static void checkFits(int[] values, int[] notes, int[] givens) {
        if (values.length != givens.length || (notes != null && notes.length != givens.length)) {
            throw new InvalidProgressException("The board must have " + givens.length + " cells.");
        }
        for (int i = 0; i < givens.length; i++) {
            if (values[i] < 0 || values[i] > 9 || (givens[i] != 0 && values[i] != givens[i])) {
                throw new InvalidProgressException("Cell " + i + " doesn't fit the puzzle.");
            }
            if (notes != null && (notes[i] < 0 || notes[i] >= 1 << 10)) {
                throw new InvalidProgressException("Cell " + i + " has notes that aren't digits.");
            }
        }
    }

    private static List<Integer> indices(List<BoardPosition> positions, int size) {
        return positions.stream().map(p -> index(p, size)).toList();
    }

    private static int index(BoardPosition position, int size) {
        return position.getRow() * size + position.getCol();
    }
}
