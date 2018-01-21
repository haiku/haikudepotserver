/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg.model;

import com.google.common.base.Preconditions;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.HpkException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
            childAttributes = new ArrayList<>();
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
        return getChildAttributes().stream().filter(a -> a.getAttributeId() == attributeId).collect(Collectors.toList());
    }

    public Optional<Attribute> tryGetChildAttribute(final AttributeId attributeId) {
        Preconditions.checkNotNull(attributeId);
        return getChildAttributes().stream().filter(a -> a.getAttributeId() == attributeId).findFirst();
    }

    public Attribute getChildAttribute(final AttributeId attributeId) {
        return tryGetChildAttribute(attributeId)
                .orElseThrow(() -> new IllegalStateException("unable to find the attribute [" + attributeId.name() + "]"));
    }

    @Override
    public String toString() {
        return getAttributeId().getName() + " : " + getAttributeType().toString();
    }

}
