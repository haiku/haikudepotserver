/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

public class DateTimeHelper {

    public static DateTimeFormatter create14DigitDateTimeFormat() {
        return
                new DateTimeFormatterBuilder()
                        .appendYearOfEra(4,4)
                        .appendMonthOfYear(2)
                        .appendDayOfMonth(2)
                        .appendHourOfDay(2)
                        .appendMinuteOfHour(2)
                        .appendSecondOfMinute(2)
                        .toFormatter();
    }

}
