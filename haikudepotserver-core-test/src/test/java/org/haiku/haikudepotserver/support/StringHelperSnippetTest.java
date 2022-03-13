/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.fest.assertions.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class StringHelperSnippetTest {

    public static Stream<Example> provideComparisonExamples() {
        return Stream.of(
                new Example("The Wild River North", "rIVer", 10, "...d River N..."),
                new Example("The Wild River North", "rIVer", 3, "...River..."),
                new Example("The Wild River North", "rth", 10, "... North"),
                new Example("The Wild River North", "th", 10, "The Wi..."),
                new Example("The Wild River North", "Zack", 10, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideComparisonExamples")
    public void testTryCreateTextSnippetAroundFoundText(Example example) {
        // GIVEN

        // WHEN
        String actual = StringHelper.tryCreateTextSnippetAroundFoundText(
                example.getText(),
                example.getSearch(), example.getSnippetLength()
        ).orElse(null);

        // THEN
        Assertions.assertThat(actual).isEqualTo(example.getExpected());
    }

    private static class Example {
        final String text;
        final String search;
        final int snippetLength;
        final String expected;

        public Example(String text, String search, int snippetLength, String expected) {
            this.text = text;
            this.search = search;
            this.snippetLength = snippetLength;
            this.expected = expected;
        }

        public String getText() {
            return text;
        }

        public String getSearch() {
            return search;
        }

        public int getSnippetLength() {
            return snippetLength;
        }

        public String getExpected() {
            return expected;
        }
    }

}
