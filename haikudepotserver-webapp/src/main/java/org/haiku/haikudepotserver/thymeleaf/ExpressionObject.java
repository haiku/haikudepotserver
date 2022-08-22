/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.thymeleaf;

import org.haiku.haikudepotserver.support.DateTimeHelper;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

/**
 * <p>This object provides special functions to Thymeleaf expressions.  An
 * example of use would be;
 * <code>${#hds.formatDataQuantity(pkgVersion.payloadLength)}</code>.  The
 * object accessed as <code>hds</code>.
 * </p>
 */

public class ExpressionObject {

    private final static DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.#");

    private final static DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeHelper.createStandardDateTimeFormat();

    public String formatTimestamp(Date value) {
        return Optional.ofNullable(value)
                .map(Date::toInstant)
                .map(TIMESTAMP_FORMATTER::format)
                .orElse("");
    }

    public String formatDataQuantity(Number value) {
        if (null != value) {
            long v = value.longValue();

            if (v < 0) {
                return "0";
            }
            if (v < 1024) {
                return toValueAndUnit(value.doubleValue(), "bytes");
            }
            if (v < 1024 * 1024) {
                return toValueAndUnit(
                        value.doubleValue() / 1024.0, "KB");
            }
            if (v < 1024 * 1024 * 1024) {
                return toValueAndUnit(
                        value.doubleValue() / (1024.0 * 1024.0), "MB");
            }

            return toValueAndUnit(value.doubleValue() / (1024.0 * 1024.0 * 1024.0), "GB");
        }

        return "";
    }

    private String toValueAndUnit(double value, String unit) {
        return NUMBER_FORMAT.format(value) + " " + unit;
    }

}
