/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.html.HtmlEscapers;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * <p>This tag will render a {@link java.util.Date} as a timestamp.</p>
 */

public class TimestampTag extends RequestContextAwareTag {

    // might be an idea to put this somewhere else if we need it again?
    private final static DateTimeFormatter FORMATTER = DateTimeHelper.createStandardDateTimeFormat();

    private java.util.Date value;

    public Date getValue() {
        return value;
    }

    public void setValue(Date value) {
        this.value = value;
    }

    @Override
    protected int doStartTagInternal() throws Exception {

        TagWriter tagWriter = new TagWriter(pageContext.getOut());

        if(null!=getValue()) {
            tagWriter.startTag("span");
            tagWriter.appendValue(HtmlEscapers.htmlEscaper().escape(FORMATTER.format(
                    Instant.ofEpochMilli(getValue().getTime()))));
            tagWriter.endTag();
        }

        return SKIP_BODY;
    }

}
