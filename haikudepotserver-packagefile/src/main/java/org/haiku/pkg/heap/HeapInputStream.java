/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.heap;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.InputStream;

public class HeapInputStream extends InputStream {

    private final HeapReader reader;

    private final HeapCoordinates coordinates;

    private long offsetInCoordinates = 0L;

    public HeapInputStream(HeapReader reader, HeapCoordinates coordinates) {
        this.reader = Preconditions.checkNotNull(reader);
        this.coordinates = Preconditions.checkNotNull(coordinates);
    }

    @Override
    public int read() throws IOException {
        if (offsetInCoordinates < coordinates.getLength()) {
            int result = reader.readHeap(coordinates.getOffset() + offsetInCoordinates);
            offsetInCoordinates++;
            return result;
        }

        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        Preconditions.checkArgument(null != b, "buffer required");
        Preconditions.checkArgument(off >= 0, "bad offset supplied");
        Preconditions.checkArgument(len >= 0, "bad length supplied");

        if (len + offsetInCoordinates >= coordinates.getLength()) {
            len = (int) (coordinates.getLength() - offsetInCoordinates);
        }

        if (0 == len) {
            return -1;
        }

        HeapCoordinates readCoordinates = new HeapCoordinates(
                coordinates.getOffset() + offsetInCoordinates, len);

        reader.readHeap(b, off, readCoordinates);
        offsetInCoordinates += len;

        return len;
    }
}
