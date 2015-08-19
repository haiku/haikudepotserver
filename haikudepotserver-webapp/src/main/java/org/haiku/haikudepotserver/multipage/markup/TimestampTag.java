/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.markup;

import com.google.common.html.HtmlEscapers;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.springframework.web.servlet.tags.RequestContextAwareTag;
import org.springframework.web.servlet.tags.form.TagWriter;

import java.util.Date;

/**
 * <p>This tag will render a {@link java.util.Date} as a timestamp.</p>
 */

public class TimestampTag extends RequestContextAwareTag {

    // might be an idea to put this somewhere else if we need it again?
    private final static DateTimeFormatter FORMATTER =
            new DateTimeFormatterBuilder()
            .appendYear(4,4)
            .appendLiteral('-')
            .appendMonthOfYear(2)
            .appendLiteral('-')
            .appendDayOfMonth(2)
            .appendLiteral(' ')
            .appendHourOfDay(2)
            .appendLiteral(':')
            .appendMinuteOfHour(2)
            .appendLiteral(':')
            .appendSecondOfMinute(2)
            .toFormatter()
            .withZoneUTC();

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
            tagWriter.appendValue(HtmlEscapers.htmlEscaper().escape(FORMATTER.print(getValue().getTime())));
            tagWriter.endTag();
        }

        return SKIP_BODY;
    }

}
