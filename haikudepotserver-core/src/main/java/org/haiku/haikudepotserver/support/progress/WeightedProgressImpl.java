/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.progress;

import com.google.common.base.Preconditions;

/**
 * A weighted progress, combines a weight which is a positive integer value together
 * with a {@link Progress}. This is used with the {@link CompositeProgressImpl} class.
 */

public class WeightedProgressImpl implements Progress {

    final int weight;
    final Progress delegate;

    public WeightedProgressImpl(int weight, Progress progress) {
        Preconditions.checkNotNull(progress);
        Preconditions.checkArgument(weight > 0, "weight must be > 0");
        this.weight = weight;
        this.delegate = progress;
    }

    @Override
    public int percentage() {
        return delegate.percentage();
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return String.format("%d%% (@ %d)", percentage(), weight);
    }
}
