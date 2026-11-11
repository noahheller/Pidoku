package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets request parameters and path variables use {@link Difficulty#id()}, ignoring case
 * ({@code ?difficulty=hard}). Spring Boot registers Converter beans with Spring MVC; an unknown
 * value becomes a 400 response.
 */
@Component
public class StringToDifficultyConverter implements Converter<String, Difficulty> {

    @Override
    public Difficulty convert(String source) {
        return Difficulty.fromId(source);
    }
}
