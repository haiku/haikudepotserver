/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import com.google.common.collect.ImmutableList;
import org.haiku.pkg.model.*;
import org.junit.Test;

import java.math.BigInteger;

import static org.fest.assertions.Assertions.assertThat;

/**
 * <p>This is a very simplistic test that checks that attributes are able to be converted into package DTO model
 * objects correctly.</p>
 */

public class PkgFactoryTest {

    private Attribute createTestPackageAttributes() {

        StringInlineAttribute majorVersionA = new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MAJOR, "6");
        majorVersionA.setChildAttributes(ImmutableList.of(
                new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MINOR, "32"),
                new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MICRO, "9"),
                new StringInlineAttribute(AttributeId.PACKAGE_VERSION_PRE_RELEASE, "beta"),
                new IntAttribute(AttributeId.PACKAGE_VERSION_REVISION, new BigInteger("8"))
        ));

        StringInlineAttribute topA = new StringInlineAttribute(AttributeId.PACKAGE, "testpkg");

        topA.setChildAttributes(ImmutableList.of(
                new StringInlineAttribute(AttributeId.PACKAGE_NAME, "testpkg"),
                new StringInlineAttribute(AttributeId.PACKAGE_VENDOR, "Test Vendor"),
                new StringInlineAttribute(AttributeId.PACKAGE_SUMMARY, "This is a test package summary"),
                new StringInlineAttribute(AttributeId.PACKAGE_DESCRIPTION, "This is a test package description"),
                new StringInlineAttribute(AttributeId.PACKAGE_URL, "http://www.haiku-os.org"),
                new IntAttribute(AttributeId.PACKAGE_ARCHITECTURE, new BigInteger("1")), // X86
                majorVersionA,
                new StringInlineAttribute(AttributeId.PACKAGE_COPYRIGHT, "Some copyright A"),
                new StringInlineAttribute(AttributeId.PACKAGE_COPYRIGHT, "Some copyright B"),
                new StringInlineAttribute(AttributeId.PACKAGE_LICENSE, "Some license A"),
                new StringInlineAttribute(AttributeId.PACKAGE_LICENSE, "Some license B")
        ));

        return topA;
    }

    @Test
    public void testCreatePackage() {

        Attribute attribute = createTestPackageAttributes();
        PkgFactory pkgFactory = new PkgFactory();

        // it is ok that this is empty because the factory should not need to reference the heap again; its all inline
        // and we are not testing the heap here.
        AttributeContext attributeContext = new AttributeContext();

        Pkg pkg = pkgFactory.createPackage(
                attributeContext,
                attribute);

        // now do some checks.

        assertThat(pkg.getArchitecture()).isEqualTo(PkgArchitecture.X86);
        assertThat(pkg.getName()).isEqualTo("testpkg");
        assertThat(pkg.getVendor()).isEqualTo("Test Vendor");
        assertThat(pkg.getSummary()).isEqualTo("This is a test package summary");
        assertThat(pkg.getDescription()).isEqualTo("This is a test package description");
        assertThat(pkg.getHomePageUrl().getUrl()).isEqualTo("http://www.haiku-os.org");
        assertThat(pkg.getHomePageUrl().getUrlType()).isEqualTo(PkgUrlType.HOMEPAGE);

        assertThat(pkg.getVersion().getMajor()).isEqualTo("6");
        assertThat(pkg.getVersion().getMinor()).isEqualTo("32");
        assertThat(pkg.getVersion().getMicro()).isEqualTo("9");
        assertThat(pkg.getVersion().getPreRelease()).isEqualTo("beta");
        assertThat(pkg.getVersion().getRevision()).isEqualTo(8);

        assertThat(pkg.getCopyrights().stream().anyMatch(x -> x.equals("Some copyright A"))).isTrue();
        assertThat(pkg.getLicenses().stream().anyMatch(x -> x.equals("Some license A"))).isTrue();
        assertThat(pkg.getLicenses().stream().anyMatch(x -> x.equals("Some license B"))).isTrue();

    }

}
