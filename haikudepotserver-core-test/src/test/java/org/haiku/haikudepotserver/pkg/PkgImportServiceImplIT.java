/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import junit.framework.AssertionFailedError;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SortOrder;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.ExposureType;
import org.haiku.haikudepotserver.support.FileHelper;
import org.haiku.pkg.model.Pkg;
import org.haiku.pkg.model.PkgArchitecture;
import org.haiku.pkg.model.PkgVersion;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@ContextConfiguration(classes = TestConfig.class)
public class PkgImportServiceImplIT extends AbstractIntegrationTest {

    private static final String RESOURCE_TEST = "tipster-1.1.1-1-x86_64.hpkg";

    @Resource
    private PkgImportService pkgImportService;

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    @Resource
    private PkgIconService pkgIconService;

    @Resource
    private PkgService pkgService;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    /**
     * <p>When a "_devel" package is imported there is a special behaviour that the localization and the
     * icons are copied from the main package over to the "_devel" package.</p>
     */

    @Test
    public void testImport_develPkgHandling() throws Exception {

        ObjectId respositorySourceObjectId;

        {
            integrationTestSupportService.createStandardTestData();

            ObjectContext context = serverRuntime.newContext();
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            respositorySourceObjectId = repositorySource.getObjectId();
        }

        // setup the base package.

        {
            Pkg importPkg = createPkg("2");

            {
                ObjectContext importObjectContext = serverRuntime.newContext();
                pkgImportService.importFrom(importObjectContext, respositorySourceObjectId, importPkg, false);
                importObjectContext.commitChanges();
            }

            // now add some localization to the imported package.

            {
                ObjectContext setupObjectContext = serverRuntime.newContext();
                org.haiku.haikudepotserver.dataobjects.Pkg persistedPkg =
                        org.haiku.haikudepotserver.dataobjects.Pkg.getByName(setupObjectContext, importPkg.getName());
                setupObjectContext.commitChanges();

                pkgLocalizationService.updatePkgLocalization(
                        setupObjectContext,
                        new NonUserPkgSupplementModificationAgent("somebody", "test"),
                        persistedPkg.getPkgSupplement(),
                        NaturalLanguage.getByCode(setupObjectContext, NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN),
                        "title_kingston_black",
                        "summary_kingston_black",
                        "description_kingston_black");
                setupObjectContext.commitChanges();


                try (InputStream inputStream = Resources.asByteSource(Resources.getResource("sample-32x32.png")).openStream()) {
                    pkgIconService.storePkgIconImage(
                            inputStream,
                            MediaType.getByCode(setupObjectContext, com.google.common.net.MediaType.PNG.toString()),
                            32,
                            setupObjectContext,
                            new UserPkgSupplementModificationAgent(null), // no user at this point
                            persistedPkg.getPkgSupplement());
                }
                setupObjectContext.commitChanges();
            }
        }

        // setup the devel package.

        Pkg importDevelPkg = new Pkg(
                "testpkg" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT,
                new PkgVersion("1", "2", "3", "4", 5),
                PkgArchitecture.X86_64,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                "test-summary-en",
                "test-description-en",
                null);

        // ---------------------------------
        {
            ObjectContext importObjectContext = serverRuntime.newContext();
            pkgImportService.importFrom(importObjectContext, respositorySourceObjectId, importDevelPkg, false);
            importObjectContext.commitChanges();
        }
        // ---------------------------------

        // check it has the icon and the localization and that there is log for the modifications present

        {
            ObjectContext context = serverRuntime.newContext();

            org.haiku.haikudepotserver.dataobjects.Pkg persistedDevelPkg =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, importDevelPkg.getName());
            PkgSupplement persistedDevelPkgSupplement = persistedDevelPkg.getPkgSupplement();

            Assertions.assertThat(persistedDevelPkgSupplement.getPkgIcons().size()).isEqualTo(1);
            Assertions.assertThat(persistedDevelPkgSupplement.getPkgIcons().get(0).getSize()).isEqualTo(32);

            PkgLocalization pkgLocalization = persistedDevelPkgSupplement.getPkgLocalization(
                    NaturalLanguageCoordinates.fromCode(NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN)).get();

            Assertions.assertThat(pkgLocalization.getTitle()).isEqualTo("title_kingston_black");
            Assertions.assertThat(pkgLocalization.getSummary()).isEqualTo("summary_kingston_black");
            Assertions.assertThat(pkgLocalization.getDescription()).isEqualTo("description_kingston_black");
        }

    }

    /**
     * <P>During the import process, it is possible that the system is able to check the length of the
     * package.  This test will check that this mechanism is working.</P>
     */

    @Test
    public void testImport_payloadData() throws Exception {

        File repositoryDirectory = null;
        int expectedPayloadLength;

        try {
            // create the repository

            integrationTestSupportService.createStandardTestData();
            Pkg inputPackage = createPkg("3");

            // setup a test repository

            {
                ObjectContext context = serverRuntime.newContext();
                RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
                repositoryDirectory = new File(repositorySource.tryGetPackagesBaseURI(ExposureType.INTERNAL_FACING).get().getPath());

                if (!repositoryDirectory.mkdirs()) {
                    throw new IllegalStateException("unable to create the on-disk repository");
                }

                File fileF = new File(repositoryDirectory, "testpkg-1.3.3~4-5-x86_64.hpkg");
                byte[] payload = Resources.toByteArray(Resources.getResource(RESOURCE_TEST));
                Files.write(payload, fileF);
                expectedPayloadLength = payload.length;
            }

            // now load the next package version in

            {
                ObjectContext context = serverRuntime.newContext();
                RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");

                // ---------------------------------

                pkgImportService.importFrom(
                        context,
                        repositorySource.getObjectId(),
                        inputPackage,
                        true); // <--- NOTE

                // ---------------------------------

                context.commitChanges();
            }

            // check the length on that package is there and is correct and that the
            // package icon is loaded in.

            {
                ObjectContext context = serverRuntime.newContext();
                org.haiku.haikudepotserver.dataobjects.Pkg pkg = org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "testpkg");
                Assertions.assertThat(pkg.getIsDesktop()).isTrue();

                RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
                org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion = pkgService.getLatestPkgVersionForPkg(
                        context,
                        pkg,
                        repositorySource,
                        Collections.singletonList(
                                Architecture.getByCode(context, "x86_64")
                        )).get();

                Assertions.assertThat(pkgVersion.getImportTimestamp()).isNotNull();
                Assertions.assertThat(pkgVersion.getPayloadLength()).isEqualTo(expectedPayloadLength);

                List<PkgIcon> pkgIcons = pkg.getPkgSupplement().getPkgIcons();
                Assertions.assertThat(pkgIcons).hasSize(1);
                PkgIcon pkgIcon = Iterables.getOnlyElement(pkgIcons);
                byte[] actualIconData = pkgIcon.getPkgIconImage().getData();
                Assertions.assertThat(actualIconData).hasSize(544);

                // check to make sure that records about the modifications are present

                List<PkgSupplementModification> pkgSupplementModifications = ObjectSelect.query(PkgSupplementModification.class)
                        .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkg.getPkgSupplement()))
                        .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                        .select(context);
                Assertions.assertThat(pkgSupplementModifications).hasSize(1);

                PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.get(0);
                Assertions.assertThat(pkgSupplementModification.getUserDescription()).isNull();
                Assertions.assertThat(pkgSupplementModification.getUser()).isNull();
                Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("hds-hpkg");
                Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo(
                        "add icon for pkg [testpkg]; size [null]; media type [application/x-vnd.haiku-icon]; sha256 [871eefa1582c96f774b44f902eb6cac05885d6ccda88700ab2e0fe99dba2319c]");
            }
        }
        finally {
            if (null != repositoryDirectory) {
                FileHelper.delete(repositoryDirectory);
            }
        }
    }

    /**
     * <p>If there is a series of packages and suddenly there is an older version come in then the future versions
     * should be deactivated.</p>
     */

    @Test
    public void testImport_versionRegressionDeactivatesNewerVersions() {

        integrationTestSupportService.createStandardTestData();

        int highestMinor= 6;

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg = integrationTestSupportService.createPkg(context, "pkgx");

            for (int i = 1; i <= 6; i++) {
                org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion = context.newObject(
                        org.haiku.haikudepotserver.dataobjects.PkgVersion.class);
                pkgVersion.setPkg(pkg);
                pkgVersion.setIsLatest(i == highestMinor);
                pkgVersion.setArchitecture(org.haiku.haikudepotserver.dataobjects.Architecture.getByCode(
                        context, Architecture.CODE_X86_64));
                pkgVersion.setMajor("1");
                pkgVersion.setMinor(Integer.toString(i));
                pkgVersion.setRepositorySource(RepositorySource.getByCode(context, "testreposrc_xyz"));
            }

            context.commitChanges();
        }

        // now there are a string of pkg versions in place, import an older one.

        Pkg inputPackage = new Pkg(
                "pkgx",
                new PkgVersion("1", "4", null, null, null), // <-- version in middle of pkg versions
                PkgArchitecture.X86_64,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                "test-summary-en",
                "test-description-en",
                null);

        {
            ObjectContext context = serverRuntime.newContext();
            ObjectId repositorySourceObjectId = RepositorySource.getByCode(context, "testreposrc_xyz").getObjectId();

            // ---------------------------------

            pkgImportService.importFrom(
                    context,
                    repositorySourceObjectId,
                    inputPackage,
                    false);

            // ---------------------------------

            context.commitChanges();
        }

        // now check to see that the right outcomes have been achieved.

        {
            ObjectContext context = serverRuntime.newContext();
            List<org.haiku.haikudepotserver.dataobjects.PkgVersion> pkgVersions = org.haiku.haikudepotserver.dataobjects.PkgVersion.findForPkg(
                    context,
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkgx"),
                    true);

            Assertions.assertThat(pkgVersions.size()).isEqualTo(6);

            for(org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion : pkgVersions) {
                switch (Integer.parseInt(pkgVersion.getMinor())) {
                    case 1, 2, 3 -> {
                        Assertions.assertThat(pkgVersion.getActive()).isTrue();
                        Assertions.assertThat(pkgVersion.getIsLatest()).isFalse();
                        Assertions.assertThat(pkgVersion.getImportTimestamp()).isNull(); // no change
                    }
                    case 4 -> {
                        Assertions.assertThat(pkgVersion.getActive()).isTrue();
                        Assertions.assertThat(pkgVersion.getIsLatest()).isTrue();
                        Assertions.assertThat(pkgVersion.getImportTimestamp()).isNotNull(); // this version import was changed
                    }
                    case 5, 6 -> {
                        Assertions.assertThat(pkgVersion.getActive()).isFalse();
                        Assertions.assertThat(pkgVersion.getIsLatest()).isFalse();
                        Assertions.assertThat(pkgVersion.getImportTimestamp()).isNull(); // no change
                    }
                    default ->
                            throw new IllegalStateException("unknown pkg version; " + pkgVersion.toVersionCoordinates().toString());
                }
            }

        }
    }

    /**
     * <p>What this test is checking is that if there is no change to a pacakge version, that the import timestamp should
     * not be updated.</p>
     */

    @Test
    public void testImport_noImportChangeNoImportTimestampChange() {
        IntegrationTestSupportService.StandardTestData testData = integrationTestSupportService.createStandardTestData();
        long nowMillis = System.currentTimeMillis();

        // Make some minor changes to the PkgVersion to adjust it to match the import.

        {
            ObjectContext context = serverRuntime.newContext();
            List<org.haiku.haikudepotserver.dataobjects.PkgVersion> pkgVersions = org.haiku.haikudepotserver.dataobjects.PkgVersion.findForPkg(
                    context,
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, testData.pkg3.getName()),
                    true);

            for(org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion : pkgVersions) {
                for (PkgVersionLocalization pkgVersionLocalization : pkgVersion.getPkgVersionLocalizations()) {
                    pkgVersionLocalization.setTitleLocalizationContent(null);
                    // ^ this is because the import will set this to null for the default; there is no title from the import.
                }
            }

            context.commitChanges();
        }

        // This should end up being a no-op on the existing PkgVersion so the import timestamp should not change.
        Pkg inputPackage;

        {
            org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion = testData.pkg3Version1;
            PkgVersionLocalization localization = testData.pkg3Version1
                    .getPkgVersionLocalization(NaturalLanguageCoordinates.english())
                    .orElseThrow();

            inputPackage = new Pkg(
                    testData.pkg3.getName(),
                    new PkgVersion(
                            pkgVersion.getMajor(),
                            pkgVersion.getMinor(),
                            pkgVersion.getMicro(),
                            pkgVersion.getPreRelease(),
                            pkgVersion.getRevision()
                    ),
                    PkgArchitecture.X86_64,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    localization.getSummary().orElseThrow(),
                    localization.getDescription().orElseThrow(),
                    null
            );
        }

        {
            ObjectContext context = serverRuntime.newContext();
            ObjectId repositorySourceObjectId = RepositorySource.getByCode(context, testData.repositorySource.getCode()).getObjectId();

            // ---------------------------------

            pkgImportService.importFrom(
                    context,
                    repositorySourceObjectId,
                    inputPackage,
                    false);

            // ---------------------------------

            context.commitChanges();
        }

        // now check to see that the import timestamp has not been altered.

        {
            ObjectContext context = serverRuntime.newContext();
            List<org.haiku.haikudepotserver.dataobjects.PkgVersion> pkgVersions = org.haiku.haikudepotserver.dataobjects.PkgVersion.findForPkg(
                    context,
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, testData.pkg3.getName()),
                    true);

            for(org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion : pkgVersions) {
                java.sql.Timestamp importTimestamp = pkgVersion.getImportTimestamp();

                if (importTimestamp != null && importTimestamp.getTime() >= nowMillis) {
                    throw new AssertionFailedError("expected the import timestamp not be updated");
                }
            }

        }
    }

    /**
     * <p>If a package already has a URL then an import with no URL should remove the URLs that are already present.</p>
     */

    @Test
    public void testImport_removalOfUrl() {

        var testData = integrationTestSupportService.createStandardTestData();

        // add a url to the pkg 3 version.

        {
            ObjectContext context = serverRuntime.newContext();

            String pkgUrlTypeCode = org.haiku.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase();
            PkgUrlType pkgUrlType = PkgUrlType.getByCode(context, pkgUrlTypeCode).orElseThrow(IllegalStateException::new);

            org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion
                    = org.haiku.haikudepotserver.dataobjects.PkgVersion.get(context, testData.pkg3Version1.getObjectId());
            PkgVersionUrl pkgVersionUrl = context.newObject(PkgVersionUrl.class);
            pkgVersionUrl.setName("Harry");
            pkgVersionUrl.setUrl("http://www.example.com");
            pkgVersionUrl.setPkgUrlType(pkgUrlType);
            pkgVersion.addToPkgVersionUrls(pkgVersionUrl);

            context.commitChanges();
        }

        // update it with no url

        Pkg inputPackage = new Pkg(
                testData.pkg3.getName(),
                new PkgVersion(
                        testData.pkg3Version1.getMajor(),
                        testData.pkg3Version1.getMinor(),
                        testData.pkg3Version1.getMicro(),
                        testData.pkg3Version1.getPreRelease(),
                        testData.pkg3Version1.getRevision()
                ),
                PkgArchitecture.X86_64,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                "test-summary-en",
                "test-description-en",
                null // <-- note no URL!
        );

        {
            ObjectContext context = serverRuntime.newContext();
            ObjectId repositorySourceObjectId = RepositorySource.getByCode(context, "testreposrc_xyz").getObjectId();

            // ---------------------------------

            pkgImportService.importFrom(
                    context,
                    repositorySourceObjectId,
                    inputPackage,
                    false);

            // ---------------------------------

            context.commitChanges();
        }

        // now check to see that the url is no longer present.

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.PkgVersion pkgVersion
                    = org.haiku.haikudepotserver.dataobjects.PkgVersion.get(context, testData.pkg3Version1.getObjectId());
            List<PkgVersionUrl> urls = pkgVersion.getPkgVersionUrls();
            Assertions.assertThat(urls).hasSize(0); // <-- has been removed.
        }
    }

    private Pkg createPkg(String minor) {
        return new Pkg(
                "testpkg",
                new PkgVersion("1", minor, "3", "4", 5),
                PkgArchitecture.X86_64,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                "test-summary-en",
                "test-description-en",
                null);
    }

}
