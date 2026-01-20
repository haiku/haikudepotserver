/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.progress;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Allows a progress to be a composite of other progresses with weights.</p>
 */

public class CompositeProgressImpl implements Progress {

    private final List<WeightedProgressImpl> progresses;

    public CompositeProgressImpl(List<WeightedProgressImpl> progresses) {
        this.progresses = progresses;
    }

    @Override
    public int percentage() {
        if (CollectionUtils.isEmpty(progresses)) {
            return 100;
        }

        if (1 == progresses.size()) {
            return progresses.getLast().percentage();
        }

        int totalWeight = progresses.stream().mapToInt(WeightedProgressImpl::getWeight).sum();
        return (int) Math.round(progresses
                .stream()
                .mapToDouble(p -> ((double) p.percentage() / 100.0) * ((double) p.getWeight() / totalWeight))
                .sum() * 100.0);
    }

    @Override
    public String toString() {
        return "[" + progresses.stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }
}
