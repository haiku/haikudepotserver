/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.heap;

import com.google.common.base.Preconditions;

/**
 * <p>This object provides an offset and length into the heap and this provides a coordinate for a chunk of
 * data in the heap.  Note that the coordinates refer to the uncompressed data across all of the chunks of the heap.
 * </p>
 */

public class HeapCoordinates {

    private long offset;
    private long length;

    public HeapCoordinates(long offset, long length) {
        super();

        Preconditions.checkState(offset >= 0 && offset < Integer.MAX_VALUE);
        Preconditions.checkState(length >= 0 && length < Integer.MAX_VALUE);

        this.offset = offset;
        this.length = length;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
    }

    @SuppressWarnings("RedundantIfStatement") // was auto-generated!
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HeapCoordinates that = (HeapCoordinates) o;

        if (length != that.length) return false;
        if (offset != that.offset) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (offset ^ (offset >>> 32));
        result = 31 * result + (int) (length ^ (length >>> 32));
        return result;
    }

    @Override

    public String toString() {
        return String.format("{%d,%d}",offset,length);
    }


}
