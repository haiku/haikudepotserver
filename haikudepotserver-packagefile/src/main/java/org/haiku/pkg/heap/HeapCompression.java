/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.heap;

import org.haiku.pkg.HpkException;

import java.util.EnumSet;
import java.util.Optional;

public enum HeapCompression {
    NONE(0),
    ZLIB(1);

    /**
     * <p>This appears in the binary file as a means of identifying the heap compression.</p>
     */

    private int numericValue;

    HeapCompression(int numericValue) {
        this.numericValue = numericValue;
    }

    public static HeapCompression getByNumericValue(int value) {
        return tryGetByNumericValue(value)
                .orElseThrow(() -> new HpkException("unknown compression numeric value [" + value + "]"));
    }

    public static Optional<HeapCompression> tryGetByNumericValue(int value) {
        return EnumSet.allOf(HeapCompression.class).stream()
                .filter(e -> e.numericValue == value)
                .findFirst();
    }
}
