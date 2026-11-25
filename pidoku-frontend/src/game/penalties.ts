/**
 * Time added to a solve on the leaderboards, matching the API (pidoku-api's Penalties): mistakes
 * cost 5 s x their count squared (5 s, 20 s, 45 s…), and each hint step 10 s (where to look), 20 s
 * (what to look for) or 60 s (the answer). Every solve still counts; an assisted one is just slower.
 */
const MISTAKE_UNIT_MS = 5_000;
export const HINT_STEP_PENALTY_MS = [10_000, 20_000, 60_000] as const;

/** The penalty for `count` wrong digits: each costs more than the last, so a slip is cheap but guessing isn't. */
export function mistakePenaltyMs(count: number): number {
  return MISTAKE_UNIT_MS * count * count;
}

/** The penalty for a game so far. */
export function penaltyMs(mistakes: number, hintPenaltyMs: number): number {
  return mistakePenaltyMs(mistakes) + hintPenaltyMs;
}
