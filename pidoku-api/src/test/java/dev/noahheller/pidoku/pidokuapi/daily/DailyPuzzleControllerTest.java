package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class DailyPuzzleControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private long fill(LocalDate date, Difficulty difficulty) {
        return dailyPuzzleService.fillWithNew(new DailySlot(date, difficulty), TestPuzzles.generated(difficulty))
                .getPuzzle().getId();
    }

    @Test
    void returnsTodaysThreePuzzlesEasiestFirst() {
        LocalDate today = dailyPuzzleService.today();
        // Filled out of order, to show the response is sorted.
        long hard = fill(today, Difficulty.HARD);
        long easy = fill(today, Difficulty.EASY);
        long medium = fill(today, Difficulty.MEDIUM);

        MvcTestResult response = mvc.get().uri("/api/puzzles/daily").exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.date").isEqualTo(today.toString());
        // Checked path by path: a lenient JSON comparison would ignore the array's order.
        assertThat(response).bodyJson().extractingPath("$.puzzles[*].difficulty").asArray()
                .containsExactly("easy", "medium", "hard");
        assertThat(response).bodyJson().extractingPath("$.puzzles[*].id")
                .convertTo(InstanceOfAssertFactories.list(Long.class))
                .containsExactly(easy, medium, hard);
    }

    @Test
    void returnsWhateverTodayHasIfTheJobFilledOnlySomeSlots() {
        fill(dailyPuzzleService.today(), Difficulty.MEDIUM);

        assertThat(mvc.get().uri("/api/puzzles/daily"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.puzzles[*].difficulty").asArray().containsExactly("medium");
    }

    @Test
    void doesNotServeOtherDaysPuzzles() {
        LocalDate today = dailyPuzzleService.today();
        fill(today.minusDays(1), Difficulty.EASY);
        fill(today.plusDays(1), Difficulty.EASY);

        assertThat(mvc.get().uri("/api/puzzles/daily")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void noDailiesForTodayIsServiceUnavailable() {
        assertThat(mvc.get().uri("/api/puzzles/daily"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .extractingPath("$.detail").asString().contains("no daily puzzles");
    }
}
