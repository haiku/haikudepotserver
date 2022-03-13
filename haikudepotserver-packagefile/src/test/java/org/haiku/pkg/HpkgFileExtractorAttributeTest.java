/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.pkg;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import org.fest.assertions.Assertions;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.haiku.pkg.model.AttributeType;
import org.haiku.pkg.model.IntAttribute;
import org.haiku.pkg.model.StringAttribute;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class HpkgFileExtractorAttributeTest extends AbstractHpkTest {

    private static final String RESOURCE_TEST = "tipster-1.1.1-1-x86_64.hpkg";

    private static final int[] HVIF_MAGIC = {
            0x6e, 0x63, 0x69, 0x66
    };

    @Test
    public void testReadFile() throws Exception {

        File hpkgFile = prepareTestFile(RESOURCE_TEST);

        try (HpkgFileExtractor hpkgFileExtractor = new HpkgFileExtractor(hpkgFile)) {

            AttributeContext tocContext = hpkgFileExtractor.getTocContext();
            List<Attribute> tocAttributes = toList(hpkgFileExtractor.getTocIterator());
            IntAttribute mTimeAttribute = (IntAttribute) tocAttributes.get(0).getChildAttributes().get(2);
            Assertions.assertThat(mTimeAttribute.getAttributeId()).isEqualTo(AttributeId.FILE_MTIME);
            Assertions.assertThat(mTimeAttribute.getAttributeType()).isEqualTo(AttributeType.INT);
            Assertions.assertThat(((Number) mTimeAttribute.getValue(tocContext)).longValue()).isEqualTo(1551679116L);

            AttributeContext packageAttributeContext = hpkgFileExtractor.getPackageAttributeContext();
            List<Attribute> packageAttributes = toList(hpkgFileExtractor.getPackageAttributesIterator());
            Attribute summaryAttribute = packageAttributes.get(1);
            Assertions.assertThat(summaryAttribute.getAttributeId()).isEqualTo(AttributeId.PACKAGE_SUMMARY);
            Assertions.assertThat(summaryAttribute.getAttributeType()).isEqualTo(AttributeType.STRING);
            Assertions.assertThat(summaryAttribute.getValue(packageAttributeContext)).isEqualTo("An application to display Haiku usage tips");

            // Pull out the actual binary to check.  The expected data results were obtained
            // from a Haiku host with the package installed.
            Attribute tipsterDirectoryEntry = findByDirectoryEntries(tocAttributes, tocContext, List.of("apps", "Tipster"));

            Attribute binaryData = tipsterDirectoryEntry.getChildAttribute(AttributeId.DATA);
            ByteSource binaryDataByteSource = (ByteSource) binaryData.getValue(tocContext);
            Assertions.assertThat(binaryDataByteSource.size()).isEqualTo(153840L);
            HashCode hashCode = binaryDataByteSource.hash(Hashing.md5());
            Assertions.assertThat(hashCode.toString().toLowerCase(Locale.ROOT)).isEqualTo("13b16cd7d035ddda09a744c49a8ebdf2");

            Optional<StringAttribute> iconAttributeOptional = tipsterDirectoryEntry.getChildAttributes(AttributeId.FILE_ATTRIBUTE)
                    .stream()
                    .map(a -> (StringAttribute) a)
                    .filter(a -> a.getValue(tocContext).equals("BEOS:ICON"))
                    .findFirst();

            Assertions.assertThat(iconAttributeOptional.isPresent()).isTrue();
            Attribute iconAttribute = iconAttributeOptional.get();

            Attribute iconBinaryData = iconAttribute.getChildAttribute(AttributeId.DATA);
            ByteSource iconDataByteSource = (ByteSource) iconBinaryData.getValue(tocContext);
            byte[] iconBytes = iconDataByteSource.read();
            assertIsHvif(iconBytes);
        }
    }

    private Attribute findByDirectoryEntries(
            List<Attribute> attributes,
            AttributeContext context,
            List<String> pathComponents) {
        Preconditions.checkArgument(!pathComponents.isEmpty());
        Optional<Attribute> resultOptional = attributes.stream()
                .filter(a -> a.getAttributeId() == AttributeId.DIRECTORY_ENTRY)
                .filter(a -> a.getValue(context).equals(pathComponents.get(0)))
                .findFirst();
        
        if (resultOptional.isPresent()) {
            if (1 == pathComponents.size()) {
                return resultOptional.get();
            }
            return findByDirectoryEntries(
                    resultOptional.get().getChildAttributes(),
                    context,
                    pathComponents.subList(1, pathComponents.size()));
        }

        throw new AssertionError("unable to find the diretory entry [" + pathComponents.get(0) + "]");
    }

    private List<Attribute> toList(AttributeIterator attributeIterator) {
        List<Attribute> assembly = new ArrayList<>();
        while (attributeIterator.hasNext()) {
            assembly.add(attributeIterator.next());
        }
        return assembly;
    }

    private void assertIsHvif(byte[] payload) {
        Assertions.assertThat(payload.length).isGreaterThan(HVIF_MAGIC.length);
        for (int i = 0; i < HVIF_MAGIC.length; i++) {
            if ((0xff & payload[i]) != HVIF_MAGIC[i]) {
                org.junit.jupiter.api.Assertions.fail("mismatch on the magic in the data payload");
            }
        }
    }

}
