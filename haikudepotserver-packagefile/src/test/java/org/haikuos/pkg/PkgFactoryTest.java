/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.pkg;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.haikuos.pkg.model.*;
import org.junit.Test;

import java.math.BigInteger;

import static org.fest.assertions.Assertions.assertThat;

/**
 * <p>This is a very simplistic test that checks that attributes are able to be converted into package DTO model
 * objects correctly.</p>
 */

public class PkgFactoryTest {

    private Attribute createTestPackageAttributes() {

        StringInlineAttribute topA = new StringInlineAttribute(AttributeId.PACKAGE,"testpkg");
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_NAME,"testpkg"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_VENDOR,"Test Vendor"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_SUMMARY,"This is a test package summary"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_DESCRIPTION,"This is a test package description"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_URL,"http://www.haiku-os.org"));
        topA.addChildAttribute(new IntAttribute(AttributeId.PACKAGE_ARCHITECTURE,new BigInteger("1"))); // X86

        StringInlineAttribute majorVersionA = new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MAJOR,"6");
        majorVersionA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MINOR,"32"));
        majorVersionA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_VERSION_MICRO,"9"));
        majorVersionA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_VERSION_PRE_RELEASE,"beta"));
        majorVersionA.addChildAttribute(new IntAttribute(AttributeId.PACKAGE_VERSION_REVISION,new BigInteger("8")));
        topA.addChildAttribute(majorVersionA);

        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_COPYRIGHT,"Some copyright A"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_COPYRIGHT,"Some copyright B"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_LICENSE,"Some license A"));
        topA.addChildAttribute(new StringInlineAttribute(AttributeId.PACKAGE_LICENSE,"Some license B"));

        return topA;
    }

    @Test
    public void testCreatePackage() throws PkgException {

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

        assertThat(Iterables.tryFind(
                pkg.getCopyrights(),
                new Predicate<String>() {
                    @Override
                    public boolean apply(java.lang.String input) {
                        return input.equals("Some copyright A");
                    }
                }).isPresent()).isTrue();

        assertThat(Iterables.tryFind(
                pkg.getCopyrights(),
                new Predicate<String>() {
                    @Override
                    public boolean apply(java.lang.String input) {
                        return input.equals("Some copyright B");
                    }
                }).isPresent()).isTrue();

        assertThat(Iterables.tryFind(
                pkg.getLicenses(),
                new Predicate<String>() {
                    @Override
                    public boolean apply(java.lang.String input) {
                        return input.equals("Some license A");
                    }
                }).isPresent()).isTrue();

        assertThat(Iterables.tryFind(
                pkg.getLicenses(),
                new Predicate<String>() {
                    @Override
                    public boolean apply(java.lang.String input) {
                        return input.equals("Some license B");
                    }
                }).isPresent()).isTrue();

    }

}
