package org.haiku.haikudepotserver.support;

import com.google.common.collect.ImmutableList;
import org.fest.assertions.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

@RunWith(Parameterized.class)
public class StringHelperSnippetTest {

    private String text;
    private String search;
    private Integer snippetLength;
    private String expected;

    public StringHelperSnippetTest(String text, String search, Integer snippetLength, String expected) {
        this.text = text;
        this.search = search;
        this.snippetLength = snippetLength;
        this.expected = expected;
    }

    @Parameterized.Parameters
    public static Collection provideComparisonExamples() {
        return ImmutableList.of(
                new Object[] { "The Wild River North", "rIVer", 10, "...d River N..." },
                new Object[] { "The Wild River North", "rIVer", 3, "...River..." },
                new Object[] { "The Wild River North", "rth", 10, "... North" },
                new Object[] { "The Wild River North", "th", 10, "The Wi..." },
                new Object[] { "The Wild River North", "Zack", 10, null }
        );
    }

    @Test
    public void testTryCreateTextSnippetAroundFoundText() {
        // GIVEN

        // WHEN
        String actual = StringHelper.tryCreateTextSnippetAroundFoundText(text, search, snippetLength).orElse(null);

        // THEN
        Assertions.assertThat(actual).isEqualTo(expected);
    }
}
