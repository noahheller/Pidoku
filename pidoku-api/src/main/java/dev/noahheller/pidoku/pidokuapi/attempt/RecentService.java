package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The player's recent puzzles, from their attempts, so the list is the same on every device.
 */
@Service
public class RecentService {

    /** How many puzzles the list holds; older ones drop off. */
    public static final int LIMIT = 10;

    private final JdbcClient jdbc;
    private final PuzzleRepository puzzles;

    public RecentService(JdbcClient jdbc, PuzzleRepository puzzles) {
        this.jdbc = jdbc;
        this.puzzles = puzzles;
    }

    private record Row(long puzzleId, boolean completed, Long timeMs, int mistakes, long penaltyMs, boolean autoNotesUsed,
                       OffsetDateTime lastPlayedAt) {
    }

    /**
     * The {@link #LIMIT} puzzles the player last played, unfinished ones first, then by when they
     * were last played. For each puzzle the attempt shown is the unfinished one if there is one
     * (a replay in progress), otherwise the latest solve.
     */
    @Transactional(readOnly = true)
    public List<RecentPuzzle> recent(Player player) {
        List<Row> rows = jdbc.sql("""
                        WITH played AS (
                            SELECT a.*, GREATEST(a.started_at, a.completed_at, a.progress_updated_at) AS last_played
                            FROM attempts a
                            WHERE a.player_id = :player
                        ),
                        latest AS (
                            SELECT DISTINCT ON (puzzle_id)
                                   puzzle_id, completed_at IS NOT NULL AS completed,
                                   CASE WHEN completed_at IS NOT NULL THEN COALESCE(active_ms, elapsed_ms)
                                        ELSE (progress ->> 'activeMs')::bigint END AS time_ms,
                                   CASE WHEN completed_at IS NOT NULL THEN mistakes
                                        ELSE COALESCE((progress ->> 'mistakes')::int, 0) END AS mistakes,
                                   -- As the leaderboards add it (Penalties: 5 s x mistakes²): for an
                                   -- unfinished game, its mistakes so far plus the hints.
                                   CASE WHEN completed_at IS NOT NULL THEN penalty_ms
                                        ELSE COALESCE((progress ->> 'mistakes')::bigint, 0) * COALESCE((progress ->> 'mistakes')::bigint, 0) * 5000
                                             + hint_penalty_ms
                                   END AS penalty_ms,
                                   auto_notes_used,
                                   max(last_played) OVER (PARTITION BY puzzle_id) AS last_played
                            FROM played
                            ORDER BY puzzle_id, completed_at IS NULL DESC, last_played DESC, id DESC
                        )
                        SELECT * FROM latest ORDER BY last_played DESC, puzzle_id DESC LIMIT :limit
                        """)
                .param("player", player.getId())
                .param("limit", LIMIT)
                .query((rs, n) -> new Row(rs.getLong("puzzle_id"), rs.getBoolean("completed"),
                        rs.getObject("time_ms", Long.class), rs.getInt("mistakes"), rs.getLong("penalty_ms"), rs.getBoolean("auto_notes_used"),
                        rs.getObject("last_played", OffsetDateTime.class)))
                .list();
        if (rows.isEmpty()) return List.of();

        List<Long> ids = rows.stream().map(Row::puzzleId).toList();
        Map<Long, Puzzle> byId = puzzles.findAllById(ids).stream().collect(Collectors.toMap(Puzzle::getId, Function.identity()));
        Map<Long, LocalDate> dailyDates = jdbc.sql("SELECT puzzle_id, puzzle_date FROM daily_puzzles WHERE puzzle_id IN (:ids)")
                .param("ids", ids)
                .query((rs, n) -> Map.entry(rs.getLong("puzzle_id"), rs.getObject("puzzle_date", LocalDate.class)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return rows.stream()
                .sorted(Comparator.comparing(Row::completed)) // unfinished first; the sort is stable
                .map(row -> new RecentPuzzle(
                        PuzzleResponse.from(byId.get(row.puzzleId()), dailyDates.get(row.puzzleId())),
                        row.completed(), row.timeMs(), row.mistakes(), row.penaltyMs(), row.autoNotesUsed(),
                        row.lastPlayedAt().toInstant()))
                .toList();
    }
}
