/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import gg.jte.Content;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import jakarta.annotation.Nullable;
import org.fest.assertions.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class TextHelperTest {

    @ParameterizedTest
    @MethodSource("htmlFormattedTextCases")
    public void testHtmlFormattedText(String input, String expected) {

        // ------------------------------------
        Content content = TextHelper.htmlFormattedText(input);
        // ------------------------------------

        StringOutput stringOutput = new StringOutput();
        content.writeTo(new OwaspHtmlTemplateOutput(stringOutput));

        Assertions.assertThat(stringOutput.toString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("highlightedHtmlFormattedTextCases")
    public void testHighlightedHtmlFormattedText(String input, @Nullable String searchExpression, String expected) {

        // ------------------------------------
        Content content = TextHelper.highlightedHtmlFormattedText(input, searchExpression);
        // ------------------------------------

        StringOutput stringOutput = new StringOutput();
        content.writeTo(new OwaspHtmlTemplateOutput(stringOutput));

        Assertions.assertThat(stringOutput.toString()).isEqualTo(expected);
    }


    private static Stream<Arguments> htmlFormattedTextCases() {
        return Stream.of(
                Arguments.of(
                        """
                                Onehunga Grey Lynn
                                Northcote\r
                                
                                   Königsburg <>
                                """,
                        "Onehunga Grey Lynn<br/>Northcote<br/><br/>   K&#246;nigsburg &lt;&gt;<br/>"
                )
        );
    }

    private static Stream<Arguments> highlightedHtmlFormattedTextCases() {
        return Stream.of(
                Arguments.of(
                        """
                                Onehunga Grey Lynn
                                Northcote\r
                                
                                   Königsburg <>
                                """,
                        null,
                        "Onehunga Grey Lynn<br/>Northcote<br/><br/>   K&#246;nigsburg &lt;&gt;<br/>"
                ),
                Arguments.of(
                        """
                                nOrTh Onehunga Grey Lynn
                                Northcote\r
                                
                                   Königsburg <>
                                """,
                        "nORth",
                        "<span class=\"common-highlight\">nOrTh</span> Onehunga Grey Lynn<br/>"
                                + "<span class=\"common-highlight\">North</span>cote<br/><br/>"
                                + "   K&#246;nigsburg &lt;&gt;<br/>"
                )
        );
    }

}