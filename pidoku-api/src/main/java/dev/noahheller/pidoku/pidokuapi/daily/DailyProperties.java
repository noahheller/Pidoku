package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;

/**
 * Settings under {@code pidoku.daily}.
 *
 * @param difficulties the difficulties that get a daily puzzle each day, e.g.
 *                     {@code pidoku.daily.difficulties=easy,medium,hard}. Kept easiest first.
 */
@Validated
@ConfigurationProperties("pidoku.daily")
public record DailyProperties(@NotEmpty List<Difficulty> difficulties) {

    /** @throws IllegalArgumentException at startup if a difficulty is listed twice */
    public DailyProperties {
        difficulties = difficulties == null ? List.of() : difficulties.stream().sorted().toList();
        if (new HashSet<>(difficulties).size() != difficulties.size()) {
            throw new IllegalArgumentException("pidoku.daily.difficulties lists a difficulty twice: " + difficulties);
        }
    }
}
