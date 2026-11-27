package dev.noahheller.pidoku.pidokuapi.stats;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Lets request parameters use {@link StatsSource#id()}, ignoring case ({@code ?source=daily}).
 * Spring Boot registers Converter beans with Spring MVC; an unknown value becomes a 400 response.
 */
@Component
public class StringToStatsSourceConverter implements Converter<String, StatsSource> {
    @Override
    public StatsSource convert(String source) {
        return StatsSource.fromId(source);
    }
}
