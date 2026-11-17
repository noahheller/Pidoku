package dev.noahheller.pidoku.pidokuapi.streak;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-17");

    /** Dates as days before today, e.g. dates(2, 1, 0) is the day before yesterday to today. */
    private static List<LocalDate> daysAgo(int... days) {
        return Arrays.stream(days).mapToObj(TODAY::minusDays).sorted().toList();
    }

    @Test
    void noSolvesIsNoStreak() {
        assertThat(Streak.of(List.of(), TODAY)).isEqualTo(new Streak(0, 0, false));
    }

    @Test
    void countsTheRunEndingToday() {
        assertThat(Streak.of(daysAgo(10, 2, 1, 0), TODAY)).isEqualTo(new Streak(3, 3, true));
    }

    @Test
    void aRunEndingYesterdayIsStillAliveUntilTheDayEnds() {
        assertThat(Streak.of(daysAgo(3, 2, 1), TODAY)).isEqualTo(new Streak(3, 3, false));
    }

    @Test
    void aMissedDayEndsTheStreakButKeepsTheBest() {
        assertThat(Streak.of(daysAgo(6, 5, 4, 3, 2), TODAY)).isEqualTo(new Streak(0, 5, false));
    }

    @Test
    void theBestIsTheLongestRunEver() {
        assertThat(Streak.of(daysAgo(9, 8, 7, 6, 4, 0), TODAY)).isEqualTo(new Streak(1, 4, true));
    }

    @Test
    void aSolveDatedAfterTodayDoesNotBreakTheCurrentRun() {
        // Possible after flying west: the player solved a date that is still ahead of their new "today".
        List<LocalDate> dates = List.of(TODAY.minusDays(1), TODAY, TODAY.plusDays(1));
        assertThat(Streak.of(dates, TODAY)).isEqualTo(new Streak(2, 3, true));
    }
}
