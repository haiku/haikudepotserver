package org.haiku.pkg;

import org.fest.assertions.Assertions;
import org.haiku.pkg.model.*;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HpkgFileExtractorAttributeTest extends AbstractHpkTest {

    private static final String RESOURCE_TEST = "tipster-1.1.1-1-x86_64.hpkg";

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
        }

    }

    private List<Attribute> toList(AttributeIterator attributeIterator) {
        List<Attribute> assembly = new ArrayList<>();
        while (attributeIterator.hasNext()) {
            assembly.add(attributeIterator.next());
        }
        return assembly;
    }


}
