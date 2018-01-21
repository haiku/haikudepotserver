/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.heap;

/**
 * <p>This is an interface for classes that are able to provide data from a block of memory referred to as "the heap".
 * Concrete sub-classes are able to provide specific implementations that can read from different on-disk files to
 * provide access to a heap.
 * </p>
 */

public interface HeapReader {

    /**
     * <p>This method reads from the heap (possibly across chunks) the data described in the coordinates attribute. It
     * writes those bytes into the supplied buffer at the offset supplied.</p>
     */

    void readHeap(byte[] buffer, int bufferOffset, HeapCoordinates coordinates);

    /**
     * <p>This method reads a single byte of the heap at the given offset.</p>
     */

    int readHeap(long offset);

}