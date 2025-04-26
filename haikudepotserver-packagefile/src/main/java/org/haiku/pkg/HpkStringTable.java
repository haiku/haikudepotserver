/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.base.Preconditions;
import org.haiku.pkg.heap.HeapCoordinates;
import org.haiku.pkg.heap.HpkHeapReader;

import java.nio.charset.StandardCharsets;

/**
 * <p>The HPK* file format may contain a table of commonly used strings in a table.  This object will represent
 * those strings and will lazily load them from the heap as necessary.</p>
 */

public class HpkStringTable implements StringTable {

    private final HpkHeapReader heapReader;

    private final long expectedCount;

    private final long heapLength;

    private final long heapOffset;

    private String[] values = null;

    HpkStringTable(
            HpkHeapReader heapReader,
            long heapOffset,
            long heapLength,
            long expectedCount) {

        super();

        Preconditions.checkNotNull(heapReader);
        Preconditions.checkState(heapOffset >= 0 && heapOffset < Integer.MAX_VALUE);
        Preconditions.checkState(heapLength >= 0 && heapLength < Integer.MAX_VALUE);
        Preconditions.checkState(expectedCount >= 0 && expectedCount < Integer.MAX_VALUE);

        this.heapReader = heapReader;
        this.expectedCount = expectedCount;
        this.heapOffset = heapOffset;
        this.heapLength = heapLength;

    }

    // TODO; could avoid the big read into a buffer by reading the heap byte by byte or with a buffer.
    private String[] readStrings() {
        String[] result = new String[(int) expectedCount];
        byte[] stringsDataBuffer = new byte[(int) heapLength];

        heapReader.readHeap(stringsDataBuffer, 0,
                new HeapCoordinates(heapOffset, heapLength));

        // now work through the data and load them into the strings.

        int stringIndex = 0;
        int offset = 0;

        while (offset < stringsDataBuffer.length) {

            if (0 == stringsDataBuffer[offset]) {
                if (stringIndex != result.length) {
                    throw new HpkException(String.format("expected to read %d package strings from the strings table, but actually found %d",expectedCount,stringIndex));
                }

                return result;
            }

            if (stringIndex >= expectedCount) {
                throw new HpkException("have already read all of the strings from the string table, but have not exhausted the string table data");
            }

            int start = offset;

            while (offset < stringsDataBuffer.length && 0 != stringsDataBuffer[offset]) {
                offset++;
            }

            if (offset < stringsDataBuffer.length) {
                result[stringIndex] = new String(stringsDataBuffer, start, offset-start, StandardCharsets.UTF_8);
                stringIndex++;
                offset++;
            }

        }

        throw new HpkException("expected to find the null-terminator for the list of strings, but was not able to find one; did read "+stringIndex+" of "+expectedCount);
    }

    private String[] getStrings() {
        if (null == values) {
            if (0 == heapLength) {
                values = new String[] {};
            } else {
                values = readStrings();
            }
        }

        return values;
    }

    @Override
    public String getString(int index) {
        return getStrings()[index];
    }

}
