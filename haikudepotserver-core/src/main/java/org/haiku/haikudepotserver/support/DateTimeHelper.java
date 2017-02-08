/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Date;
import java.util.Locale;

import static java.time.temporal.ChronoField.*;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

public class DateTimeHelper {

    public static Date secondAccuracyDate(Date date) {
        return new Date((date.getTime() / 1000) * 1000);
    }

    public static Date secondAccuracyDatePlusOneSecond(Date date) {
        return new Date(secondAccuracyDate(date).getTime() + 1000);
    }

    public static DateTimeFormatter createStandardDateTimeFormat() {
        return new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
                .appendLiteral('-')
                .appendValue(MONTH_OF_YEAR, 2)
                .appendLiteral('-')
                .appendValue(DAY_OF_MONTH, 2)
                .appendLiteral(' ')
                .appendValue(HOUR_OF_DAY, 2)
                .appendLiteral(':')
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendLiteral(':')
                .appendValue(SECOND_OF_MINUTE, 2)
                .toFormatter(Locale.ENGLISH)
                .withZone(ZoneOffset.UTC);
    }

    public static DateTimeFormatter create14DigitDateTimeFormat() {
        return new DateTimeFormatterBuilder()
                .appendValue(YEAR, 4)
                .appendValue(MONTH_OF_YEAR, 2)
                .appendValue(DAY_OF_MONTH, 2)
                .appendValue(HOUR_OF_DAY, 2)
                .appendValue(MINUTE_OF_HOUR, 2)
                .appendValue(SECOND_OF_MINUTE, 2)
                .toFormatter(Locale.ENGLISH)
                .withZone(ZoneOffset.UTC);
    }

}
