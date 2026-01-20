/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.progress;

import com.google.common.base.Preconditions;

/**
 * <p>A progress, which just returns a value that it has been assigned.</p>
 */

public class SimpleProgressImpl implements Progress {

    private int value;

    public SimpleProgressImpl() {
        this.value = 0;
    }

    public SimpleProgressImpl(int value) {
        setValue(value);
    }

    @Override
    public int percentage() {
        return value;
    }

    public void setValue(int value) {
        Preconditions.checkArgument(value >= 0, "value must be >= 0");
        Preconditions.checkArgument(value <= 100, "value must be <= 100");
        this.value = value;
    }

    /**
     * <p>Set the value based on the completed count of total number of items to work on.</p>
     */

    public void setItemsCompleted(int count, int total) {
        Preconditions.checkArgument(total >= 0, "total must be >= 0");
        Preconditions.checkArgument(count >= 0 && count <= total, "illegal count value");
        if (0 == total) {
            setValue(100);
        } else {
            setValue((100 * count) / total);
        }
    }

    @Override
    public String toString() {
        return String.format("%d%%", percentage());
    }

}
