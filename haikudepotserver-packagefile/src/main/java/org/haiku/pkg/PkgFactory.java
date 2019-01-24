/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.base.Preconditions;
import org.haiku.pkg.model.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>This object is algorithm that is able to convert a top level package attribute into a modelled package object
 * that can more easily represent the package; essentially converting the low-level attributes into a higher-level
 * package model object.</p>
 */

class PkgFactory {

    Pkg createPackage(
            AttributeContext attributeContext,
            Attribute attribute)  {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(attribute.getAttributeId() == AttributeId.PACKAGE);

        try {
            return new Pkg(
                    getStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_NAME),
                    createVersion(attributeContext, attribute.getChildAttribute(AttributeId.PACKAGE_VERSION_MAJOR)),
                    createArchitecture(attributeContext, attribute.getChildAttribute(AttributeId.PACKAGE_ARCHITECTURE)),
                    getStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VENDOR),
                    getChildAttributesAsStrings(attributeContext, attribute.getChildAttributes(AttributeId.PACKAGE_COPYRIGHT)),
                    getChildAttributesAsStrings(attributeContext, attribute.getChildAttributes(AttributeId.PACKAGE_LICENSE)),
                    getStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_SUMMARY),
                    getStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_DESCRIPTION),
                    tryCreateHomePagePkgUrl(attributeContext, attribute).orElse(null));
        } catch(HpkException he) {
            throw new PkgException("unable to create a package owing to a problem with the hpk packaging",he);
        }
    }

    private Optional<String> tryGetStringAttributeValue(
            AttributeContext attributeContext,
            Attribute attribute,
            AttributeId attributeId) {
        return attribute.tryGetChildAttribute(attributeId)
                .map(a -> a.getValue(attributeContext))
                .map(a -> (String) a);
    }

    private String getStringAttributeValue(
            AttributeContext attributeContext,
            Attribute attribute,
            AttributeId attributeId) {
        return tryGetStringAttributeValue(attributeContext, attribute, attributeId)
                .orElseThrow(() -> new PkgException(
                        String.format("the %s attribute must be present",attributeId.getName())));
    }

    private PkgVersion createVersion(
            AttributeContext attributeContext,
            Attribute attribute) {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(AttributeId.PACKAGE_VERSION_MAJOR == attribute.getAttributeId());

        return new PkgVersion(
                (String) attribute.getValue(attributeContext),
                tryGetStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VERSION_MINOR).orElse(null),
                tryGetStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VERSION_MICRO).orElse(null),
                tryGetStringAttributeValue(attributeContext,attribute, AttributeId.PACKAGE_VERSION_PRE_RELEASE).orElse(null),
                attribute.tryGetChildAttribute(AttributeId.PACKAGE_VERSION_REVISION)
                        .map(a -> (IntAttribute) a)
                        .map(a -> a.getValue(attributeContext))
                        .map(BigInteger::intValue)
                        .orElse(null)
        );
    }

    private PkgArchitecture createArchitecture(
            AttributeContext attributeContext,
            Attribute attribute) {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(AttributeId.PACKAGE_ARCHITECTURE == attribute.getAttributeId());

        int value = ((BigInteger) attribute.getValue(attributeContext)).intValue();
        return PkgArchitecture.values()[value];
    }

    private List<String> getChildAttributesAsStrings(
            AttributeContext attributeContext,
            List<Attribute> attributes) {
        return attributes.stream().map(a -> a.getValue(attributeContext).toString()).collect(Collectors.toList());
    }

    private Optional<PkgUrl> tryCreateHomePagePkgUrl(
            AttributeContext attributeContext,
            Attribute attribute) {
        return tryGetStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_URL)
                .map(v -> new PkgUrl(v, PkgUrlType.HOMEPAGE));
    }

}
