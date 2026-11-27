package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The puzzle history on a profile: every puzzle the player has solved, dailies and random ones,
 * newest first, as their {@code history_visibility} allows (and their profile's, since it's shown
 * there). Unfinished puzzles never show. Each puzzle comes with the player's first solve, the one
 * leaderboards rank, so a viewer can open it and try to beat that time.
 *
 * <p>Each puzzle also lists the first solves of the people around the viewer: the viewer's own, and
 * their friends' whose history the viewer may see (a friend's that isn't private). On the
 * player's own profile that's their friends; on someone else's it's the viewer and the viewer's
 * friends. "In common" keeps only the puzzles that have any.
 *
 * <p>Three small queries per page: the viewer's friends, a page of the player's first solves, and
 * the others' first solves of those puzzles, plus the puzzles themselves.
 */
@Service
public class HistoryService {

    /** How many puzzles a page holds. */
    public static final int PAGE_SIZE = 20;

    private final JdbcClient jdbc;
    private final ProfileAccess access;
    private final PuzzleRepository puzzles;

    public HistoryService(JdbcClient jdbc, ProfileAccess access, PuzzleRepository puzzles) {
        this.jdbc = jdbc;
        this.access = access;
        this.puzzles = puzzles;
    }

    /** A first solve as the queries return it; {@code playerId} and {@code displayName} only for others'. */
    record SolveRow(long puzzleId, long playerId, String displayName, Instant completedAt, long elapsedMs,
                    long penaltyMs, int mistakes, int hintsUsed, boolean autoNotesUsed, boolean late) {
    }

    /**
     * A page of a player's history.
     *
     * @param viewerUsesGoogle whether the viewer is signed in with Google: only then may they see
     *                         other players' profiles
     * @param offset           how many of the newest solves to skip (0 for the first page)
     * @param inCommon         only puzzles the viewer or a friend of theirs has solved too
     * @throws GoogleSignInRequiredException if it's someone else's and the viewer is anonymous
     * @throws ProfileNotFoundException      if there's no such player, or the viewer may not see their profile
     * @throws HistoryHiddenException        if the viewer may see the profile but not the history
     */
    @Transactional(readOnly = true)
    public PuzzleHistory history(Player viewer, boolean viewerUsesGoogle, long playerId, int offset, boolean inCommon) {
        ProfileAccess.Target target = access.target(viewer, viewerUsesGoogle, playerId);
        if (!target.historyVisible()) {
            throw new HistoryHiddenException(playerId);
        }
        List<Long> company = company(viewer, playerId);
        if (inCommon && company.isEmpty()) {
            return new PuzzleHistory(List.of(), false);
        }

        // One more than a page, to tell whether there's another.
        StatementSpec query = jdbc.sql("""
                        WITH firsts AS (
                            SELECT DISTINCT ON (a.puzzle_id)
                                   a.puzzle_id, a.player_id, NULL::text AS display_name, a.completed_at,
                                   COALESCE(a.active_ms, a.elapsed_ms) AS elapsed_ms, a.penalty_ms, a.mistakes,
                                   a.hints_used, a.auto_notes_used, a.late
                            FROM attempts a
                            WHERE a.player_id = :playerId AND a.completed_at IS NOT NULL
                            ORDER BY a.puzzle_id, a.completed_at, a.id
                        )
                        SELECT * FROM firsts f
                        WHERE true
                        """ + (inCommon ? """
                          AND EXISTS (SELECT 1 FROM attempts o
                                      WHERE o.puzzle_id = f.puzzle_id AND o.player_id IN (:company)
                                        AND o.completed_at IS NOT NULL)
                        """ : "") + """
                        ORDER BY f.completed_at DESC, f.puzzle_id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("playerId", playerId)
                .param("limit", PAGE_SIZE + 1)
                .param("offset", Math.max(0, offset));
        if (inCommon) {
            query = query.param("company", company);
        }
        List<SolveRow> page = query.query(SolveRow.class).list();
        boolean hasMore = page.size() > PAGE_SIZE;
        if (hasMore) {
            page = page.subList(0, PAGE_SIZE);
        }
        if (page.isEmpty()) {
            return new PuzzleHistory(List.of(), false);
        }

        List<Long> ids = page.stream().map(SolveRow::puzzleId).toList();
        Map<Long, List<SolveRow>> others = othersSolves(ids, company);
        Map<Long, Puzzle> byId = puzzles.findAllById(ids).stream()
                .collect(Collectors.toMap(Puzzle::getId, Function.identity()));
        Map<Long, LocalDate> dailyDates = jdbc.sql("SELECT puzzle_id, puzzle_date FROM daily_puzzles WHERE puzzle_id IN (:ids)")
                .param("ids", ids)
                .query((rs, n) -> Map.entry(rs.getLong("puzzle_id"), rs.getObject("puzzle_date", LocalDate.class)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        long viewerId = viewer.getId();
        List<PuzzleHistory.Entry> entries = page.stream()
                .map(row -> new PuzzleHistory.Entry(
                        PuzzleResponse.from(byId.get(row.puzzleId()), dailyDates.get(row.puzzleId())),
                        new PuzzleHistory.Solve(row.completedAt(), row.elapsedMs(), row.penaltyMs(), row.mistakes(),
                                row.hintsUsed(), row.autoNotesUsed(), row.late()),
                        others.getOrDefault(row.puzzleId(), List.of()).stream()
                                .map(o -> new PuzzleHistory.OtherSolve(o.playerId(), o.displayName(), o.playerId() == viewerId,
                                        o.elapsedMs(), o.penaltyMs(), o.mistakes(), o.hintsUsed(), o.late()))
                                .toList()))
                .toList();
        return new PuzzleHistory(entries, hasMore);
    }

    /**
     * Whose solves may show next to the player's: the viewer (unless it's their own history), and
     * the viewer's friends whose profile and history a friend may see. Never the player themselves.
     */
    private List<Long> company(Player viewer, long playerId) {
        record Friend(long id, String profileVisibility, String historyVisibility) {
        }
        List<Long> ids = new ArrayList<>();
        if (viewer.getId() != playerId) {
            ids.add(viewer.getId());
        }
        jdbc.sql("""
                        SELECT p.id, p.profile_visibility, p.history_visibility
                        FROM friendships f
                        JOIN players p ON p.id = f.friend_id
                        WHERE f.player_id = :viewerId AND p.id <> :playerId
                        """)
                .param("viewerId", viewer.getId())
                .param("playerId", playerId)
                .query(Friend.class)
                .list().stream()
                .filter(f -> ProfileVisibility.fromId(f.profileVisibility()).visibleTo(false, true)
                        && ProfileVisibility.fromId(f.historyVisibility()).visibleTo(false, true))
                .forEach(f -> ids.add(f.id()));
        return ids;
    }

    /** The first solves of these puzzles by these players, by puzzle, fastest (as ranked) first. */
    private Map<Long, List<SolveRow>> othersSolves(List<Long> puzzleIds, List<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT DISTINCT ON (a.puzzle_id, a.player_id)
                               a.puzzle_id, a.player_id, p.display_name, a.completed_at,
                               COALESCE(a.active_ms, a.elapsed_ms) AS elapsed_ms, a.penalty_ms, a.mistakes,
                               a.hints_used, a.auto_notes_used, a.late
                        FROM attempts a
                        JOIN players p ON p.id = a.player_id
                        WHERE a.puzzle_id IN (:puzzleIds) AND a.player_id IN (:playerIds) AND a.completed_at IS NOT NULL
                        ORDER BY a.puzzle_id, a.player_id, a.completed_at, a.id
                        """)
                .param("puzzleIds", puzzleIds)
                .param("playerIds", playerIds)
                .query(SolveRow.class)
                .list().stream()
                .sorted(Comparator.comparingLong((SolveRow r) -> r.elapsedMs() + r.penaltyMs())
                        .thenComparing(SolveRow::completedAt))
                .collect(Collectors.groupingBy(SolveRow::puzzleId));
    }
}
