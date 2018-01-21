/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haiku.pkg.model.*;

import java.math.BigInteger;
import java.util.Optional;

/**
 * <p>This object is algorithm that is able to convert a top level package attribute into a modelled package object
 * that can more easily represent the package; essentially converting the low-level attributes into a higher-level
 * package model object.</p>
 */

class PkgFactory {

    private String getOptionalStringAttributeValue(
            AttributeContext attributeContext,
            Attribute attribute,
            AttributeId attributeId) throws PkgException, HpkException {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);

        Optional<Attribute> nameAttributeOptional = attribute.tryGetChildAttribute(attributeId);

        if (!nameAttributeOptional.isPresent()) {
            return null;
        }

        return (String) nameAttributeOptional.get().getValue(attributeContext);
    }

    private String getRequiredStringAttributeValue(
            AttributeContext attributeContext,
            Attribute attribute,
            AttributeId attributeId) throws PkgException, HpkException {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);

        Optional<Attribute> nameAttributeOptional = attribute.tryGetChildAttribute(attributeId);

        if (!nameAttributeOptional.isPresent()) {
            throw new PkgException(String.format("the %s attribute must be present",attributeId.getName()));
        }

        return (String) nameAttributeOptional.get().getValue(attributeContext);
    }

    private PkgVersion createVersion(
            AttributeContext attributeContext,
            Attribute attribute) throws PkgException, HpkException {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(AttributeId.PACKAGE_VERSION_MAJOR == attribute.getAttributeId());

        Optional<Attribute> revisionAttribute = attribute.tryGetChildAttribute(AttributeId.PACKAGE_VERSION_REVISION);
        Integer revision = null;

        if (revisionAttribute.isPresent()) {
            revision = ((IntAttribute) revisionAttribute.get()).getValue(attributeContext).intValue();
        }

        return new PkgVersion(
                (String) attribute.getValue(attributeContext),
                getOptionalStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VERSION_MINOR),
                getOptionalStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VERSION_MICRO),
                getOptionalStringAttributeValue(attributeContext,attribute, AttributeId.PACKAGE_VERSION_PRE_RELEASE),
                revision);

    }

    private PkgArchitecture createArchitecture(
            AttributeContext attributeContext,
            Attribute attribute) throws HpkException {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(AttributeId.PACKAGE_ARCHITECTURE == attribute.getAttributeId());

        int value = ((BigInteger) attribute.getValue(attributeContext)).intValue();
        return PkgArchitecture.values()[value];
    }

    Pkg createPackage(
            AttributeContext attributeContext,
            Attribute attribute) throws PkgException {

        Preconditions.checkNotNull(attribute);
        Preconditions.checkNotNull(attributeContext);
        Preconditions.checkState(attribute.getAttributeId() == AttributeId.PACKAGE);

        Pkg result = new Pkg();

        try {

            result.setName(getRequiredStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_NAME));
            result.setVendor(getRequiredStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_VENDOR));
            result.setSummary(getOptionalStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_SUMMARY));
            result.setDescription(getOptionalStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_DESCRIPTION));

            String packageUrl = getOptionalStringAttributeValue(attributeContext, attribute, AttributeId.PACKAGE_URL);

            if (!Strings.isNullOrEmpty(packageUrl)) {
                result.setHomePageUrl(new PkgUrl(packageUrl, PkgUrlType.HOMEPAGE));
            }

            // get the architecture.

            Optional<Attribute> architectureAttributeOptional = attribute.tryGetChildAttribute(AttributeId.PACKAGE_ARCHITECTURE);

            if(!architectureAttributeOptional.isPresent()) {
                throw new PkgException(String.format("the attribute %s is required", AttributeId.PACKAGE_ARCHITECTURE));
            }

            result.setArchitecture(createArchitecture(attributeContext,architectureAttributeOptional.get()));

            // get the version.

            Optional<Attribute> majorVersionAttributeOptional = attribute.tryGetChildAttribute(AttributeId.PACKAGE_VERSION_MAJOR);

            if (!majorVersionAttributeOptional.isPresent()) {
                throw new PkgException(String.format("the attribute %s is required", AttributeId.PACKAGE_VERSION_MAJOR));
            }

            result.setVersion(createVersion(attributeContext, majorVersionAttributeOptional.get()));

            // get the copyrights.

            for (Attribute copyrightAttribute : attribute.getChildAttributes(AttributeId.PACKAGE_COPYRIGHT)) {
                if (copyrightAttribute.getAttributeType() == AttributeType.STRING) { // illegal not to be, but be lenient
                    result.addCopyright(copyrightAttribute.getValue(attributeContext).toString());
                }
            }

            // get the licenses.

            for (Attribute licenseAttribute : attribute.getChildAttributes(AttributeId.PACKAGE_LICENSE)) {
                if (licenseAttribute.getAttributeType() == AttributeType.STRING) { // illegal not to be, but be lenient
                    result.addLicense(licenseAttribute.getValue(attributeContext).toString());
                }
            }


        } catch(HpkException he) {
            throw new PkgException("unable to create a package owing to a problem with the hpk packaging",he);
        }

        return result;
    }

}
