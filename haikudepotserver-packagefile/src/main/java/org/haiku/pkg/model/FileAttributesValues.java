/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.pkg.model;

/**
 * <p>The attribute type {@link AttributeId#FILE_ATTRIBUTE} has a value
 * which is a string that defines what sort of attribute it is.</p>
 */

public enum FileAttributesValues {

    BEOS_ICON("BEOS:ICON");

    private String attributeValue;

    FileAttributesValues(String attributeValue) {
        this.attributeValue = attributeValue;
    }

    public String getAttributeValue() {
        return attributeValue;
    }

}
