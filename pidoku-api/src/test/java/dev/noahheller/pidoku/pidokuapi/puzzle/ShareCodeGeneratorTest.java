package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShareCodeGeneratorTest {

    private final ShareCodeGenerator generator = new ShareCodeGenerator(new Random(7));

    @Test
    void alphabetLeavesOutEasilyConfusedCharacters() {
        assertThat(ShareCodeGenerator.ALPHABET).doesNotContain("0", "O", "1", "I", "L");
        assertThat(ShareCodeGenerator.ALPHABET).isUpperCase();
        assertThat(ShareCodeGenerator.ALPHABET.chars().distinct().count())
                .isEqualTo(ShareCodeGenerator.ALPHABET.length());
    }

    @RepeatedTest(20)
    void codesHaveTheRightLengthAndOnlyUseTheAlphabet() {
        String code = generator.next();
        assertThat(code).hasSize(ShareCodeGenerator.LENGTH);
        assertThat(code.chars()).allMatch(c -> ShareCodeGenerator.ALPHABET.indexOf(c) >= 0);
    }

    @Test
    void codesDiffer() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSize(1_000);
    }

    @Test
    void normalizeUpperCasesAndTrims() {
        assertThat(ShareCodeGenerator.normalize(" 7kq4m2xd\n")).isEqualTo("7KQ4M2XD");
    }
}
