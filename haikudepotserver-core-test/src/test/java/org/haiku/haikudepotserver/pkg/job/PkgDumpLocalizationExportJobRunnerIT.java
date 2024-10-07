/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgDumpLocalizationExportJobSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@ContextConfiguration(classes = TestConfig.class)
public class PkgDumpLocalizationExportJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        // This will set up a situation where we see a subordinate packet; we don't want
        // to see the localizations for the subordinate packet; only the main package should
        // be listed.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg999 = integrationTestSupportService.createPkg(context, "pkg999");

            Pkg pkg999x86 = context.newObject(Pkg.class);
            pkg999x86.setActive(true);
            pkg999x86.setIsNativeDesktop(false);
            pkg999x86.setName("pkg999_x86");
            pkg999x86.setPkgSupplement(pkg999.getPkgSupplement());

            PkgLocalization localization = context.newObject(PkgLocalization.class);
            localization.setNaturalLanguage(NaturalLanguage.getByCode(context, "de"));
            localization.setTitle("Grunewald");
            localization.setSummary("Wald neben Berlin");
            localization.setDescription("Ein Wald in unmittelbarer Nähe von Berlin mit Seen, Sand und Spazierwegen.");
            localization.setPkgSupplement(pkg999.getPkgSupplement());

            context.commitChanges();
        }

        PkgDumpLocalizationExportJobSpecification specification = new PkgDumpLocalizationExportJobSpecification();

        // ------------------------------------
        String guid = jobService.submit(
                specification,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        // pull in the json data and check it.

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JsonNode rootNode = jobService.tryObtainData(dataGuid).map(this::jobJsonNode).orElseThrow();

        // the pkg1 has some default test localizations we can check to start with.

        Assertions.assertThat(findContentOrNull(rootNode, "pkg1", "en", "title"))
                .isEqualTo("Package 1");
        Assertions.assertThat(findContentOrNull(rootNode, "pkg1", "de", "title"))
                .isEqualTo("Packet 1");
        Assertions.assertThat(findContentOrNull(rootNode, "pkg1", "es", "title"))
                .isEqualTo("Ping 1");

        // check where some localization data is missing

        Assertions.assertThat(findContentOrNull(rootNode, "pkg1", "es", "summary"))
                .isNull();

        // check a package with all the localizations

        Assertions.assertThat(findContentOrNull(rootNode, "pkg999", "de", "title"))
                .isEqualTo("Grunewald");
        Assertions.assertThat(findContentOrNull(rootNode, "pkg999", "de", "summary"))
                .isEqualTo("Wald neben Berlin");
        Assertions.assertThat(findContentOrNull(rootNode, "pkg999", "de", "description"))
                .isEqualTo("Ein Wald in unmittelbarer Nähe von Berlin mit Seen, Sand und Spazierwegen.");

        // check no data for the subordinate package

        Assertions.assertThat(tryFindItemForPkgName(rootNode, "pkg999_x86").isEmpty()).isTrue();

    }

    private JsonNode jobJsonNode(JobDataWithByteSource jobSource) {
        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)
        ) {
            return objectMapper.readTree(gzipInputStream);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private String findContentOrNull(JsonNode root, String pkgName, String naturalLanguageCode, String localizationTypeCode) {
        return tryFindContent(root, pkgName, naturalLanguageCode, localizationTypeCode).orElse(null);
    }

    private Optional<String> tryFindContent(JsonNode root, String pkgName, String naturalLanguageCode, String localizationTypeCode) {
        return tryFindItemForPkgName(root, pkgName).map(jn -> {
            JsonNode localizationsNode = jn.get("localizations");

            for (int i = 0; i < localizationsNode.size(); i++) {
                JsonNode localizationNode = localizationsNode.get(i);
                if (localizationNode.get("naturalLanguage").get("code").asText().equals(naturalLanguageCode)
                    && localizationNode.get("code").asText().equals(localizationTypeCode)) {
                    return localizationNode.get("content").asText();
                }
            }

            return null;
        });
    }

    private Optional<JsonNode> tryFindItemForPkgName(JsonNode root, String pkgName) {
        JsonNode itemsNode = root.get("items");

        for (int i = 0; i < itemsNode.size(); i++) {
            if (itemsNode.get(i).get("pkgName").asText().equals(pkgName)) {
                return Optional.of(itemsNode.get(i));
            }
        }

        return Optional.empty();
    }

}
