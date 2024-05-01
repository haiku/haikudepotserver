/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.pkg.model.PkgDumpLocalizationImportJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@ContextConfiguration(classes = TestConfig.class)
public class PkgDumpLocalizationImportJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "samuel", "1c6002fb-bd4e-441f-bac7-7a4cc6b1e232");
            Pkg pkg = integrationTestSupportService.createPkg(context, "bluesky");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
            createPermissionForPkg(context, user, pkg);
            context.commitChanges();
        }

        PkgDumpLocalizationImportJobSpecification spec = new PkgDumpLocalizationImportJobSpecification();
        spec.setOriginSystemDescription("flamingo");
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE,
                getResourceByteSource("pkg/job/pkgdumplocalizationimport/sample-single-pkg-change.json")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        JobSnapshot snapshot = jobService.tryGetJob(guid).orElseThrow();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);
        JsonNode generatedJsonNode = getOutputTreeForSnapshot(snapshot);

        // first check that the output data is correct.

        JsonNode item0Node = generatedJsonNode.at("/items/0");
        Assertions.assertThat(item0Node.get("pkgName").asText()).isEqualTo("bluesky");
        Assertions.assertThat(item0Node.get("status").asText()).isEqualTo("UPDATED");

        // check to make sure that the localizations have been loaded into the database.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            PkgLocalization ptBrLoc = pkg.getPkgSupplement()
                    .getPkgLocalization(NaturalLanguageCoordinates.fromCode("pt_BR"))
                    .orElseThrow(() -> new AssertionError("expected localization to be present"));
            Assertions.assertThat(ptBrLoc.getTitle()).isEqualTo("Es geht nicht um das Geld!");
            // ^^ note the string was trimmed.
            Assertions.assertThat(ptBrLoc.getSummary()).isEqualTo("So kann es nicht sein!");
            Assertions.assertThat(ptBrLoc.getDescription()).isEqualTo("H\u00f6nig ist besonders gut auf Toast");
        }

        // check to make sure that change record have been written. Here we expect that there will be a
        // change record for the two authors in the input file.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg)
                    .stream()
                    .sorted(Comparator.comparing(PkgSupplementModification::getContent))
                    .toList();
            Assertions.assertThat(modifications.size()).isEqualTo(2);

            for (PkgSupplementModification modification : modifications) {
                Assertions.assertThat(modification.getUser()).isNull();
                Assertions.assertThat(modification.getOriginSystemDescription()).isEqualTo("flamingo");
                Assertions.assertThat(modification.getUserDescription()).endsWith("(auth:samuel)");
            }

            PkgSupplementModification modification0 = modifications.getFirst();
            Assertions.assertThat(modification0.getUserDescription()).isEqualTo("Susan Peabody (auth:samuel)");
            Assertions.assertThat(modification0.getContent()).isEqualTo("""
                    changing localization for pkg [bluesky] in natural language [pt-BR];
                    description: [H\u00f6nig ist besonders gut auf Toast]""");

            PkgSupplementModification modification1 = modifications.get(1);
            Assertions.assertThat(modification1.getUserDescription()).isEqualTo("Matai Rena (auth:samuel)");
            Assertions.assertThat(modification1.getContent()).isEqualTo("""
                    changing localization for pkg [bluesky] in natural language [pt-BR];
                    title: [Es geht nicht um das Geld!]
                    summary: [So kann es nicht sein!]""");
        }

    }

    /**
     * <p>Tests the situation when a localization change comes through but the user involved does not have
     * permission to edit the data.</p>
     */

    @Test
    public void testRun_noAuthorization() throws IOException {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "samuel", "1c6002fb-bd4e-441f-bac7-7a4cc6b1e232");
            integrationTestSupportService.createPkg(context, "bluesky");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);

            // note; no permission set for this user.

            context.commitChanges();
        }

        PkgDumpLocalizationImportJobSpecification spec = new PkgDumpLocalizationImportJobSpecification();
        spec.setOriginSystemDescription("flamingo");
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE,
                getResourceByteSource("pkg/job/pkgdumplocalizationimport/sample-single-pkg-change.json")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        JobSnapshot snapshot = jobService.tryGetJob(guid).orElseThrow();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);
        JsonNode generatedJsonNode = getOutputTreeForSnapshot(snapshot);

        JsonNode item0Node = generatedJsonNode.at("/items/0");
        Assertions.assertThat(item0Node.get("pkgName").asText()).isEqualTo("bluesky");
        Assertions.assertThat(item0Node.get("status").asText()).isEqualTo("ERROR");
        Assertions.assertThat(item0Node.get("error").get("message").asText())
                .isEqualTo("unauthorized to edit the localization for [Pkg[name=bluesky]]");

        // check to make sure that no localization data was written to the database

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            Assertions.assertThat(pkg.getPkgSupplement().getPkgLocalizations()).isEmpty();
            Assertions.assertThat(PkgSupplementModification.findForPkg(context, pkg)).isEmpty();
        }

    }

    /**
     * <p>Packages with suffixes like `_devel` are not allowed to have their localization
     * set; so this should fail.</p>
     */

    @Test
    public void testRun_disallowedSubordinatePkg() throws IOException {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "samuel", "1c6002fb-bd4e-441f-bac7-7a4cc6b1e232");
            Pkg pkgRegular = integrationTestSupportService.createPkg(context, "bluesky");
            Pkg pkgDevel = integrationTestSupportService.createPkg(context, "bluesky_devel");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
            Stream.of(pkgRegular, pkgDevel).forEach(p -> createPermissionForPkg(context, user, p));
            context.commitChanges();
        }

        PkgDumpLocalizationImportJobSpecification spec = new PkgDumpLocalizationImportJobSpecification();
        spec.setOriginSystemDescription("flamingo");
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE,
                getResourceByteSource("pkg/job/pkgdumplocalizationimport/sample-devel-pkg-change.json")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        JobSnapshot snapshot = jobService.tryGetJob(guid).orElseThrow();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);
        JsonNode generatedJsonNode = getOutputTreeForSnapshot(snapshot);

        JsonNode item0Node = generatedJsonNode.at("/items/0");
        Assertions.assertThat(item0Node.get("pkgName").asText()).isEqualTo("bluesky_devel");
        Assertions.assertThat(item0Node.get("status").asText()).isEqualTo("ERROR");
        Assertions.assertThat(item0Node.get("error").get("message").asText())
                .isEqualTo("the pkg [bluesky_devel] is unable to have localizations be imported");

        // check to make sure that no localization data was written to the database

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            Assertions.assertThat(pkg.getPkgSupplement().getPkgLocalizations()).isEmpty();
            Assertions.assertThat(PkgSupplementModification.findForPkg(context, pkg)).isEmpty();
        }

    }

    /**
     * <p>Tests the situation when a no-op localization change comes through.</p>
     */

    @Test
    public void testRun_noChange() throws IOException {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "samuel", "1c6002fb-bd4e-441f-bac7-7a4cc6b1e232");
            Pkg pkg = integrationTestSupportService.createPkg(context, "bluesky");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);

            PkgLocalization localization = context.newObject(PkgLocalization.class);
            localization.setPkgSupplement(pkg.getPkgSupplement());
            localization.setNaturalLanguage(NaturalLanguage.getByNaturalLanguage(context, NaturalLanguageCoordinates.fromCode("pt_BR")));
            localization.setDescription("H\u00f6nig ist besonders gut auf Toast");
            localization.setSummary("So kann es nicht sein!");
            localization.setTitle("Es geht nicht um das Geld!");

            createPermissionForPkg(context, user, pkg);

            context.commitChanges();
        }

        PkgDumpLocalizationImportJobSpecification spec = new PkgDumpLocalizationImportJobSpecification();
        spec.setOriginSystemDescription("flamingo");
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE,
                getResourceByteSource("pkg/job/pkgdumplocalizationimport/sample-single-pkg-change.json")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        JobSnapshot snapshot = jobService.tryGetJob(guid).orElseThrow();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);
        JsonNode generatedJsonNode = getOutputTreeForSnapshot(snapshot);

        JsonNode item0Node = generatedJsonNode.at("/items/0");
        Assertions.assertThat(item0Node.get("pkgName").asText()).isEqualTo("bluesky");
        Assertions.assertThat(item0Node.get("status").asText()).isEqualTo("UNCHANGED");

        // check to make sure that no pkg changes are recorded

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            Assertions.assertThat(PkgSupplementModification.findForPkg(context, pkg)).isEmpty();
        }

    }

    /**
     * <p>This test is checking what happens when there are more than one changes for the same
     * field on the same package. The final change should be the only change that gets stored.
     * </p>
     */

    @Test
    public void testRun_cascadingChange() throws IOException {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "samuel", "1c6002fb-bd4e-441f-bac7-7a4cc6b1e232");
            Pkg pkg = integrationTestSupportService.createPkg(context, "bluesky");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
            createPermissionForPkg(context, user, pkg);
            context.commitChanges();
        }

        PkgDumpLocalizationImportJobSpecification spec = new PkgDumpLocalizationImportJobSpecification();
        spec.setOriginSystemDescription("flamingo");
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE,
                getResourceByteSource("pkg/job/pkgdumplocalizationimport/sample-cascading-pkg-change.json")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        JobSnapshot snapshot = jobService.tryGetJob(guid).orElseThrow();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);
        JsonNode generatedJsonNode = getOutputTreeForSnapshot(snapshot);

        // first check that the output data is correct.

        JsonNode item0Node = generatedJsonNode.at("/items/0");
        Assertions.assertThat(item0Node.get("pkgName").asText()).isEqualTo("bluesky");
        Assertions.assertThat(item0Node.get("status").asText()).isEqualTo("UPDATED");

        // check to make sure that the localizations have been loaded into the database.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            PkgLocalization ptBrLoc = pkg.getPkgSupplement()
                    .getPkgLocalization(NaturalLanguageCoordinates.fromCode("pt_BR"))
                    .orElseThrow(() -> new AssertionError("expected localization to be present"));
            Assertions.assertThat(ptBrLoc.getTitle()).isEqualTo("Stumble");
            // ^^ note the string was trimmed.
            Assertions.assertThat(ptBrLoc.getSummary()).isNull();
            Assertions.assertThat(ptBrLoc.getDescription()).isNull();
        }

        // check to make sure that change record have been written. Here we expect that there will be a
        // change record for the two authors in the input file.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "bluesky");
            List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg)
                    .stream()
                    .sorted(Comparator.comparing(PkgSupplementModification::getContent))
                    .toList();
            PkgSupplementModification modification = modifications.getLast();

            Assertions.assertThat(modification.getUser()).isNull();
            Assertions.assertThat(modification.getOriginSystemDescription()).isEqualTo("flamingo");
            Assertions.assertThat(modification.getUserDescription()).isEqualTo("Apple Pie (auth:samuel)");
            Assertions.assertThat(modification.getContent()).isEqualTo("""
                    changing localization for pkg [bluesky] in natural language [pt-BR];
                    title: [Stumble]""");
        }

    }

    private void createPermissionForPkg(ObjectContext context, User user, Pkg pkg) {
        PermissionUserPkg permissionUserPkg = context.newObject(PermissionUserPkg.class);
        permissionUserPkg.setPkg(pkg);
        permissionUserPkg.setUser(user);
        permissionUserPkg.setPermission(Permission.getByCode(
                context,
                org.haiku.haikudepotserver.security.model.Permission.PKG_EDITLOCALIZATION.name().toLowerCase(Locale.ROOT)));
        user.addToPermissionUserPkgs(permissionUserPkg);
    }

    private JsonNode getOutputTreeForSnapshot(JobSnapshot snapshot) throws IOException {
        String dataGuid = snapshot
                .getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());

        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            return objectMapper.readTree(gzipInputStream);
        }
    }

}
