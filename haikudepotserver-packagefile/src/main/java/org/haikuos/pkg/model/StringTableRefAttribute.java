/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.model;

import com.google.common.base.Preconditions;
import org.haikuos.pkg.AttributeContext;
import org.haikuos.pkg.HpkException;

/**
 * <p>This type of attribute references a string from an instance of {@link org.haikuos.pkg.HpkStringTable}
 * which is typically obtained from an instance of {@link org.haikuos.pkg.AttributeContext}.</p>
 */

public class StringTableRefAttribute extends StringAttribute {

    private int index;

    public StringTableRefAttribute(AttributeId attributeId, int index) {
        super(attributeId);
        Preconditions.checkState(index >= 0);
        this.index = index;
    }

    @Override
    public String getValue(AttributeContext context) throws HpkException {
        return context.getStringTable().getString(index);
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.STRING;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringTableRefAttribute that = (StringTableRefAttribute) o;

        if (index != that.index) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return String.format("%s : @%d",super.toString(),index);
    }

}
