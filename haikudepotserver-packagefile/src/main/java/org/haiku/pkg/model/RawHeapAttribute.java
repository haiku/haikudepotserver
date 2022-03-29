/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.heap.HeapCoordinates;
import org.haiku.pkg.heap.HeapInputStream;
import org.haiku.pkg.heap.HeapReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>This type of attribute refers to raw data.  It uses coordinates into the heap to provide a source for the
 * data.</p>
 */

public class RawHeapAttribute extends RawAttribute {

    private final HeapCoordinates heapCoordinates;

    public RawHeapAttribute(AttributeId attributeId, HeapCoordinates heapCoordinates) {
        super(attributeId);
        Preconditions.checkNotNull(heapCoordinates);
        this.heapCoordinates = heapCoordinates;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawHeapAttribute that = (RawHeapAttribute) o;

        if (!heapCoordinates.equals(that.heapCoordinates)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return heapCoordinates.hashCode();
    }

    @Override
    public ByteSource getValue(AttributeContext context) {
        return new HeapByteSource(context.getHeapReader(), heapCoordinates);
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.RAW;
    }

    @Override
    public String toString() {
        return String.format("%s : @%s",super.toString(),heapCoordinates.toString());
    }

    public static class HeapByteSource extends ByteSource {

        private final HeapReader heapReader;
        private final HeapCoordinates heapCoordinates;

        public HeapByteSource(HeapReader heapReader, HeapCoordinates heapCoordinates) {
            this.heapCoordinates = heapCoordinates;
            this.heapReader = heapReader;
        }

        @Override
        public InputStream openStream() throws IOException {
            return new HeapInputStream(heapReader, heapCoordinates);
        }

        @Override
        public com.google.common.base.Optional<Long> sizeIfKnown() {
            return com.google.common.base.Optional.of(heapCoordinates.getLength());
        }

        public HeapCoordinates getHeapCoordinates() {
            return heapCoordinates;
        }

    }

}
