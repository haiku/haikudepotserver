/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.userrating.model;

import java.util.Map;

public record DerivedUserRating(
        float rating,
        long sampleSize,
        Map<Short, Long> ratingDistribution) {
}
