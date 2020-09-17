/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;

@ContextConfiguration(classes = TestConfig.class)
public class ReferenceDumpExportJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private JobService jobService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RuntimeInformationService runtimeInformationService;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws Exception {

        ReferenceDumpExportJobSpecification specification = new ReferenceDumpExportJobSpecification();
        specification.setNaturalLanguageCode(NaturalLanguage.CODE_GERMAN);
        java.util.Date latestModifyTimestamp = new java.util.Date(runtimeInformationService.getBuildTimestamp().toEpochMilli());

        // ------------------------------------
        String guid = jobService.submit(
                specification,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        // pull in the ZIP file now and extract the data

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)
        ) {

            JsonNode rootNode = objectMapper.readTree(gzipInputStream);

            JsonNode dataModifiedTimestampNode = rootNode.at("/info/dataModifiedTimestamp");

            // This is plus one second because that's what the modify timestamp does
            // in the process.
            Assertions.assertThat(dataModifiedTimestampNode.asLong())
                    .isEqualTo(latestModifyTimestamp.getTime());

            // countries are not localized.
            JsonNode countriesArrayNode = rootNode.get("countries");
            assertItemWithCodeAndName(countriesArrayNode, "NZ", "New Zealand");

            // languages are presented in their native script / language
            JsonNode naturalLanguagesArrayNode = rootNode.get("naturalLanguages");
            assertItemWithCodeAndName(naturalLanguagesArrayNode, "de", "Deutsch");
            assertItemWithCodeAndName(naturalLanguagesArrayNode, "ru",
                    "\u0420\u0443\u0441\u0441\u043a\u0438\u0439");

            // the request was in German so the results should be in German too.
            JsonNode pkgCategoriesArrayNode = rootNode.get("pkgCategories");
            assertItemWithCodeAndName(pkgCategoriesArrayNode, "graphics", "Grafik");

            // check a user rating stability
            JsonNode userRatingStabilitiesArrayNode = rootNode.get("userRatingStabilities");
            assertItemWithCodeAndName(userRatingStabilitiesArrayNode, "unstablebutusable", "Nicht stabil, aber benutzbar");
        }

    }

    private void assertItemWithCodeAndName(JsonNode array, String code, String name) {
        IntStream.range(0, array.size())
                .mapToObj(array::get)
                .filter(itemNode -> {
                    String itemCode = itemNode.get("code").asText();
                    String itemName = itemNode.get("name").asText();
                    return itemCode.equals(code) && itemName.equals(name);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("unable to find entry with code [" +
                        code + "] and name [" + name + "]"));
    }


}
