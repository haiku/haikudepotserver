/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.fest.assertions.Assertions;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.HpkgFileExtractor;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class HpkgHelperTest {

    private static final String RESOURCE_TEST = "tipster-1.1.1-1-x86_64.hpkg";

    private static final int[] HVIF_MAGIC = {
            0x6e, 0x63, 0x69, 0x66
    };

    @TempDir
    File temporaryFolder;

    @Test
    public void testFindIconAttributesFromAppExecutableDirEntries() throws Exception {
        // GIVEN
        File file = prepareTestFile(RESOURCE_TEST);
        HpkgFileExtractor fileExtractor = new HpkgFileExtractor(file);
        AttributeContext tocContext = fileExtractor.getTocContext();

        // WHEN
        List<Attribute> attributes = HpkgHelper.findIconAttributesFromExecutableDirEntries(
                tocContext, fileExtractor.getToc());

        // THEN
        Assertions.assertThat(attributes).hasSize(1);
        Attribute iconA = Iterables.getOnlyElement(attributes);
        Attribute iconDataA = iconA.getChildAttribute(AttributeId.DATA);
        ByteSource byteSource = (ByteSource) iconDataA.getValue(tocContext);
        byte[] data = byteSource.read();
        Assertions.assertThat(data).hasSize(544);
        assertIsHvif(data);
    }

    File prepareTestFile(String resource) throws IOException {
        byte[] payload = Resources.toByteArray(Resources.getResource(resource));
        File temporaryFile = new File(temporaryFolder, resource);
        Files.write(payload, temporaryFile);
        return temporaryFile;
    }

    private void assertIsHvif(byte[] payload) {
        Assertions.assertThat(payload.length).isGreaterThan(HVIF_MAGIC.length);
        for (int i = 0; i < HVIF_MAGIC.length; i++) {
            if ((0xff & payload[i]) != HVIF_MAGIC[i]) {
                org.junit.jupiter.api.Assertions.fail(
                        "mismatch on the magic in the data payload");
            }
        }
    }

}
