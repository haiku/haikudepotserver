/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg.model;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.haikuos.pkg.AttributeContext;
import org.haikuos.pkg.HpkException;

import java.util.Collections;
import java.util.List;

/**
 * <p>This is the superclass of the different types (data types) of attributes.</p>
 */

public abstract class Attribute {

    private AttributeId attributeId;

    private List<Attribute> childAttributes = null;

    public Attribute(AttributeId attributeId) {
        super();
        this.attributeId = attributeId;
    }

    public AttributeId getAttributeId() {
        return attributeId;
    }

    public abstract AttributeType getAttributeType();

    public abstract Object getValue(AttributeContext context)  throws HpkException;

    public void addChildAttribute(Attribute attribute) {
        Preconditions.checkNotNull(attribute);

        if(null==childAttributes) {
            childAttributes = Lists.newArrayList();
        }

        childAttributes.add(attribute);
    }

    public boolean hasChildAttributes() {
        return null!=childAttributes && !childAttributes.isEmpty();
    }

    public List<Attribute> getChildAttributes() {
        if(null==childAttributes) {
            return Collections.emptyList();
        }
        return childAttributes;
    }

    public List<Attribute> getChildAttributes(final AttributeId attributeId) {
        Preconditions.checkNotNull(attributeId);
        return Lists.newArrayList(Iterables.filter(
                getChildAttributes(),
                new Predicate<Attribute>() {
                    @Override
                    public boolean apply(Attribute input) {
                        return input.getAttributeId() == attributeId;
                    }
                }
        ));
    }

    public Optional<Attribute> getChildAttribute(final AttributeId attributeId) {
        Preconditions.checkNotNull(attributeId);
        return Iterables.tryFind(
                getChildAttributes(),
                new Predicate<Attribute>() {
                    @Override
                    public boolean apply(Attribute input) {
                        return input.getAttributeId() == attributeId;
                    }
                }
        );
    }

    @Override
    public String toString() {
        return getAttributeId().getName() + " : " + getAttributeType().toString();
    }

}
