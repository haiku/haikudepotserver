/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import org.haiku.pkg.AttributeContext;

import java.util.Arrays;

public class RawInlineAttribute extends RawAttribute {

    private final byte[] rawValue;

    public RawInlineAttribute(AttributeId attributeId, byte[] rawValue) {
        super(attributeId);
        Preconditions.checkNotNull(rawValue);
        this.rawValue = rawValue;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawInlineAttribute that = (RawInlineAttribute) o;

        if (!Arrays.equals(rawValue, that.rawValue)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rawValue);
    }

    @Override
    public ByteSource getValue(AttributeContext context) {
        return ByteSource.wrap(rawValue);
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.RAW;
    }

    @Override
    public String toString() {
        return String.format("%s : %d b",super.toString(),rawValue.length);
    }

}
