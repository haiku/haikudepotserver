/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.junit.Test;

import java.io.File;
import java.io.OutputStreamWriter;
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

        File hpkrFile = null;
        HpkrFileExtractor hpkrFileExtractor = null;
        Attribute ncursesSourceAttribute = null;

        try {
            hpkrFile = prepareTestFile();
            hpkrFileExtractor = new HpkrFileExtractor(hpkrFile);

            OutputStreamWriter streamWriter = new OutputStreamWriter(System.out);
            AttributeIterator attributeIterator = hpkrFileExtractor.getPackageAttributesIterator();
            AttributeContext attributeContext = hpkrFileExtractor.getAttributeContext();

            while(attributeIterator.hasNext()) {
                Attribute attribute = attributeIterator.next();

                if(AttributeId.PACKAGE == attribute.getAttributeId()) {
                    String packageName = attribute.getValue(attributeIterator.getContext()).toString();

                    if(packageName.equals("ncurses_source")) {
                        ncursesSourceAttribute = attribute;
                    }
                }
            }

            // now the analysis phase.

            assertThat(ncursesSourceAttribute).isNotNull();
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_NAME).get().getValue(attributeContext)).isEqualTo("ncurses_source");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_ARCHITECTURE).get().getValue(attributeContext)).isEqualTo(new BigInteger("3"));
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_URL).get().getValue(attributeContext)).isEqualTo("http://www.gnu.org/software/ncurses/ncurses.html");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_SOURCE_URL).get().getValue(attributeContext)).isEqualTo("Download <http://ftp.gnu.org/pub/gnu/ncurses/ncurses-5.9.tar.gz>");
            assertThat(ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_CHECKSUM).get().getValue(attributeContext)).isEqualTo("6a25c52890e7d335247bd96965b5cac2f04dafc1de8d12ad73346ed79f3f4215");

            // check the version which is a sub-tree of attributes.

            Attribute majorVersionAttribute = ncursesSourceAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_MAJOR).get();
            assertThat(majorVersionAttribute.getValue(attributeContext)).isEqualTo("5");
            assertThat(majorVersionAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_MINOR).get().getValue(attributeContext)).isEqualTo("9");
            assertThat(majorVersionAttribute.getChildAttribute(AttributeId.PACKAGE_VERSION_REVISION).get().getValue(attributeContext)).isEqualTo(new BigInteger("10"));

        }
        finally {
            if(null!=hpkrFileExtractor) {
                hpkrFileExtractor.close();
            }

            if(null!=hpkrFile) {
                hpkrFile.delete();
            }
        }

    }

}
