/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.junit.Test;

import java.io.File;
import java.math.BigInteger;

import static org.fest.assertions.Assertions.assertThat;

/**
 * <p>This is a simplistic test that is just going to stream out some attributes from a known HPKR file and will
 * then look for certain packages and make sure that an artificially created attribute set matches.  It's basically
 * a smoke test.</p>
 */

public class HpkrFileExtractorAttributeTest extends AbstractHpkrTest {

    @Test
    public void testRepo() throws Exception {

        File hpkrFile = prepareTestFile();

        try (HpkrFileExtractor hpkrFileExtractor = new HpkrFileExtractor(hpkrFile)) {
            Attribute ncursesSourceAttribute = null;

            AttributeIterator attributeIterator = hpkrFileExtractor.getPackageAttributesIterator();
            AttributeContext attributeContext = hpkrFileExtractor.getAttributeContext();

            while (attributeIterator.hasNext()) {
                Attribute attribute = attributeIterator.next();

                if (AttributeId.PACKAGE == attribute.getAttributeId()) {
                    String packageName = attribute.getValue(attributeIterator.getContext()).toString();

                    if (packageName.equals("ncurses_source")) {
                        ncursesSourceAttribute = attribute;
                    }
                }
            }

            // now the analysis phase.

            assertThat(ncursesSourceAttribute).isNotNull();
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_NAME).getValue(attributeContext)).isEqualTo("ncurses_source");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_ARCHITECTURE).getValue(attributeContext)).isEqualTo(new BigInteger("3"));
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_URL).getValue(attributeContext)).isEqualTo("http://www.gnu.org/software/ncurses/ncurses.html");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_SOURCE_URL).getValue(attributeContext)).isEqualTo("Download <http://ftp.gnu.org/pub/gnu/ncurses/ncurses-5.9.tar.gz>");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_CHECKSUM).getValue(attributeContext)).isEqualTo("6a25c52890e7d335247bd96965b5cac2f04dafc1de8d12ad73346ed79f3f4215");

            // check the version which is a sub-tree of attributes.

            Attribute majorVersionAttribute = ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_MAJOR);
            assertThat(majorVersionAttribute.getValue(attributeContext)).isEqualTo("5");
            assertThat(majorVersionAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_MINOR).getValue(attributeContext)).isEqualTo("9");
            assertThat(majorVersionAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_REVISION).getValue(attributeContext)).isEqualTo(new BigInteger("10"));

        }

    }

}
