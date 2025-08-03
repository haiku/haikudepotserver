/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

import org.apache.commons.lang3.StringUtils;

import java.util.stream.Stream;

public enum JobDataEncoding {

    NONE,
    GZIP;

    public String lowerName() {
        return name().toLowerCase();
    }

    public static JobDataEncoding getByHeaderValue(String headerValue) {

        if (StringUtils.isEmpty(headerValue)) {
            return JobDataEncoding.NONE;
        }

        return Stream.of(JobDataEncoding.values())
                .filter(e -> StringUtils.equalsAnyIgnoreCase(headerValue, e.name()))
                .findFirst()
                .orElse(JobDataEncoding.NONE);
    }

}
