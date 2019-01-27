/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import org.springframework.web.servlet.tags.RequestContextAwareTag;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * <p>This tag renders some text with newlines turned into valid HTML structure.</p>
 */

public class PlainTextContentTag extends RequestContextAwareTag {

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

            pageContext.getOut().print(
                    Arrays.stream(value.split("[\n\r]"))
                            .map(s -> HtmlEscapers.htmlEscaper().escape(s))
                            .collect(Collectors.joining("<br/>\n"))
            );

        }

        return SKIP_BODY;
    }

}
