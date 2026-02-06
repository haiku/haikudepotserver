/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Optional;

public class StringHelper {

    /**
     * <p>If the <code>search</code> is found in the <code>text</code> then this method
     * will return the found text with a bit of text around it.  Otherwise it will
     * return an empty {@link Optional}.</p>
     */

    public static Optional<String> tryCreateTextSnippetAroundFoundText(String text, String search, int snippetLength) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(search)) {
            return Optional.empty();
        }

        search = StringUtils.trimToEmpty(search);
        int firstIndex = Strings.CI.indexOf(text, search);

        if (-1 == firstIndex) {
            return Optional.empty();
        }

        int overflow = Math.abs(Math.max(snippetLength, search.length()) - search.length()) >> 1;
        int snippetStart = Math.max(0, firstIndex - overflow);
        int snippetEnd = Math.min(text.length(), firstIndex + search.length() + overflow);
        String middle = text.substring(snippetStart, snippetEnd);

        if (0 != snippetStart) {
            middle = "..." + middle;
        }

        if (snippetEnd != text.length()) {
            middle = middle + "...";
        }

        return Optional.of(middle);
    }

}
