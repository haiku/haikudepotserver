/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.jte;

import gg.jte.Content;
import gg.jte.html.HtmlContent;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class TimestampHelper {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

   public static Content formatTimestamp(@Nullable Instant instant) {
       return (HtmlContent) output -> {
           if (null != instant) {
               output.writeContent(TIMESTAMP_FORMAT.format(instant));
           }
       };
   }

    public static Content formatDate(@Nullable Instant instant) {
        return (HtmlContent) output -> {
            if (null != instant) {
                output.writeContent(DATE_FORMAT.format(instant));
            }
        };
    }

}
