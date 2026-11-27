import { useEffect, useState } from 'react';
import { fetchDailyOn, localDate, type DailyPuzzles } from './client';
import { useLoad, type Load } from './useLoad';

/** How often an open page checks whether the player's date has changed. */
const DATE_CHECK_MS = 60_000;

/**
 * Loads today's daily puzzles on mount, and again when the player's local date changes while the
 * page is open (left open past midnight), so it never keeps showing yesterday's. `retry` loads
 * them again.
 */
export function useDaily(): { state: Load<DailyPuzzles>; retry: () => void } {
  const [today, setToday] = useState(() => localDate(new Date()));

  useEffect(() => {
    // Setting the same date again changes nothing, so this only re-renders at midnight.
    const check = () => setToday(localDate(new Date()));
    const timer = setInterval(check, DATE_CHECK_MS);
    // A sleeping laptop or a background tab can skip the timer; check on coming back too.
    document.addEventListener('visibilitychange', check);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', check);
    };
  }, []);

  // Keyed by the date, so yesterday's puzzles give way to the loading state until today's arrive.
  const { state, reload } = useLoad(today, (signal) => fetchDailyOn(today, signal));
  return { state, retry: reload };
}
