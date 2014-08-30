/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage.markup;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.html.HtmlEscapers;
import org.springframework.web.servlet.tags.RequestContextAwareTag;

import java.util.regex.Pattern;

/**
 * <p>This tag renders some text with newlines turned into valid HTML structure.</p>
 */

public class PlainTextContentTag extends RequestContextAwareTag {

    private final static Pattern PATTERN_NEWLINE = Pattern.compile("\\n\\d|\\d\\n|\\n");

    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        if (!Strings.isNullOrEmpty(value)) {

            pageContext.getOut().print(Joiner.on("<br/>\n").join(
                    Iterables.transform(
                            Splitter.on(PATTERN_NEWLINE).split(value),
                            new Function<String, String>() {
                                @Override
                                public String apply(String input) {
                                    return HtmlEscapers.htmlEscaper().escape(input);
                                }
                            }
                    )
            ));
        }

        return SKIP_BODY;
    }

}
