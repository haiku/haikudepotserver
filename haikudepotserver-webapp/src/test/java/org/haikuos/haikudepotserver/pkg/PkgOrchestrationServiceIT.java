/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.io.Files;
import com.google.common.net.*;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.MediaType;
import org.haikuos.haikudepotserver.support.FileHelper;
import org.haikuos.pkg.model.Pkg;
import org.haikuos.pkg.model.PkgArchitecture;
import org.haikuos.pkg.model.PkgVersion;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Random;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgOrchestrationServiceIT extends AbstractIntegrationTest {

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    private org.haikuos.pkg.model.Pkg createPkg(String minor) {
        org.haikuos.pkg.model.Pkg pkg = new Pkg();
        pkg.setArchitecture(PkgArchitecture.X86);
        pkg.setDescription("test-description-en");
        pkg.setSummary("test-summary-en");
        pkg.setName("testpkg");
        pkg.setVersion(new PkgVersion("1", minor, "3", "4", 5));
        return pkg;
    }

    /**
     * <p>When a "_devel" package exists and an update is made to the icon of the parent
     * package then the icon should flow down to the "_devel" package too.</p>
     */

    @Test
    public void testStorePkgIconImage_develPkgHandling() throws Exception {

        // setup the two packages.

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1Devel = context.newObject(org.haikuos.haikudepotserver.dataobjects.Pkg.class);
            pkg1Devel.setActive(true);
            pkg1Devel.setName("pkg1" + PkgOrchestrationService.SUFFIX_PKG_DEVELOPMENT);
            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1").get();
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();

            try(InputStream inputStream = PkgOrchestrationServiceIT.class.getResourceAsStream("/sample-32x32.png")) {

                // ---------------------------------
                pkgOrchestrationService.storePkgIconImage(
                        inputStream,
                        pngMediaType,
                        32,
                        context,
                        pkg1);
                // ---------------------------------

            }

            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.getContext();
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString()).get();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1Devel =
                    org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(
                            context,
                            "pkg1" + PkgOrchestrationService.SUFFIX_PKG_DEVELOPMENT).get();

            PkgIcon pkgIcon = pkg1Devel.getPkgIcon(pngMediaType, 32).get();

            Assertions.assertThat(pkgIcon.getSize()).isEqualTo(32);
        }
    }

    /**
     * <p>When a "_devel" package exists and an update is made to the localization of the parent
     * package then the localization should flow down to the "_devel" package too.</p>
     */

    @Test
    public void testUpdatePkgLocalization_develPkgHandling() throws Exception {

        // setup the two packages.

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1Devel = context.newObject(org.haikuos.haikudepotserver.dataobjects.Pkg.class);
            pkg1Devel.setActive(true);
            pkg1Devel.setName("pkg1" + PkgOrchestrationService.SUFFIX_PKG_DEVELOPMENT);
            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1").get();
            NaturalLanguage naturalLanguageGerman = NaturalLanguage.getByCode(context, NaturalLanguage.CODE_GERMAN).get();

            // ---------------------------------
            pkgOrchestrationService.updatePkgLocalization(
                    context,
                    pkg1,
                    naturalLanguageGerman,
                    "title_kokako",
                    "summary_kokako",
                    "description_kokako");
            // ---------------------------------

            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg1Devel =
                    org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(
                            context,
                            "pkg1" + PkgOrchestrationService.SUFFIX_PKG_DEVELOPMENT).get();

            PkgLocalization pkgLocalization = pkg1Devel.getPkgLocalization(NaturalLanguage.CODE_GERMAN).get();

            Assertions.assertThat(pkgLocalization.getTitle()).isEqualTo("title_kokako");
            Assertions.assertThat(pkgLocalization.getSummary()).isEqualTo("summary_kokako" + PkgOrchestrationService.SUFFIX_SUMMARY_DEVELOPMENT);
            Assertions.assertThat(pkgLocalization.getDescription()).isEqualTo("description_kokako");
        }

    }

    /**
     * <p>When a "_devel" package is imported there is a special behaviour that the localization and the
     * icons are copied from the main package over to the "_devel" package.</p>
     */

    @Test
    public void testImport_develPkgHandling() throws Exception {

        ObjectId respositorySourceObjectId;

        {
            integrationTestSupportService.createStandardTestData();

            ObjectContext context = serverRuntime.getContext();
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc").get();
            respositorySourceObjectId = repositorySource.getObjectId();
        }

        // setup the base package.

        {
            org.haikuos.pkg.model.Pkg importPkg = createPkg("2");

            {
                ObjectContext importObjectContext = serverRuntime.getContext();
                pkgOrchestrationService.importFrom(importObjectContext, respositorySourceObjectId, importPkg, false);
                importObjectContext.commitChanges();
            }

            // now add some localization to the imported package.

            {
                ObjectContext setupObjectContext = serverRuntime.getContext();
                org.haikuos.haikudepotserver.dataobjects.Pkg persistedPkg =
                        org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(setupObjectContext, importPkg.getName()).get();
                setupObjectContext.commitChanges();

                pkgOrchestrationService.updatePkgLocalization(
                        setupObjectContext,
                        persistedPkg,
                        NaturalLanguage.getByCode(setupObjectContext, NaturalLanguage.CODE_GERMAN).get(),
                        "title_kingston_black",
                        "summary_kingston_black",
                        "description_kingston_black");
                setupObjectContext.commitChanges();

                try (InputStream inputStream = PkgOrchestrationServiceIT.class.getResourceAsStream("/sample-32x32.png")) {
                    pkgOrchestrationService.storePkgIconImage(
                            inputStream,
                            MediaType.getByCode(setupObjectContext, com.google.common.net.MediaType.PNG.toString()).get(),
                            32,
                            setupObjectContext,
                            persistedPkg);
                }
                setupObjectContext.commitChanges();
            }
        }

        // setup the devel package.

        org.haikuos.pkg.model.Pkg importDevelPkg = createPkg("2");
        importDevelPkg.setName(importDevelPkg.getName() + PkgOrchestrationService.SUFFIX_PKG_DEVELOPMENT);

        // ---------------------------------
        {
            ObjectContext importObjectContext = serverRuntime.getContext();
            pkgOrchestrationService.importFrom(importObjectContext, respositorySourceObjectId, importDevelPkg, false);
            importObjectContext.commitChanges();
        }
        // ---------------------------------

        // check it has the icon and the localization.

        {
            ObjectContext context = serverRuntime.getContext();

            org.haikuos.haikudepotserver.dataobjects.Pkg persistedDevelPkg =
                    org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context, importDevelPkg.getName()).get();

            Assertions.assertThat(persistedDevelPkg.getPkgIcons().size()).isEqualTo(1);
            Assertions.assertThat(persistedDevelPkg.getPkgIcons().get(0).getSize()).isEqualTo(32);

            PkgLocalization pkgLocalization = persistedDevelPkg.getPkgLocalization(NaturalLanguage.CODE_GERMAN).get();

            Assertions.assertThat(pkgLocalization.getTitle()).isEqualTo("title_kingston_black");
            Assertions.assertThat(pkgLocalization.getSummary()).isEqualTo("summary_kingston_black (development files)");
            Assertions.assertThat(pkgLocalization.getDescription()).isEqualTo("description_kingston_black");
        }

    }

    /**
     * <P>During the import process, it is possible that the system is able to check the length of the
     * package.  This test will check that this mechanism is working.</P>
     */

    @Test
    public void testImport_payloadLength() throws Exception {

        File repositoryDirectory = null;
        int expectedPayloadLength;

        try {
            // create the repository

            integrationTestSupportService.createStandardTestData();
            org.haikuos.pkg.model.Pkg inputPackage = createPkg("3");

            // setup a test repository

            {
                ObjectContext context = serverRuntime.getContext();
                RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc").get();
                repositoryDirectory = new File(repositorySource.getPackagesBaseURL().getPath());

                if (!repositoryDirectory.mkdirs()) {
                    throw new IllegalStateException("unable to create the on-disk repository");
                }

                Random random = new Random(System.currentTimeMillis());
                File fileF = new File(repositoryDirectory, "testpkg-1.3.3~4-5-x86.hpkg");
                byte[] buffer = new byte[1000 + (Math.abs(random.nextInt()) % 10*1000)];
                Files.write(buffer,fileF);
                expectedPayloadLength = buffer.length;
            }

            // now load the next package version in

            {
                ObjectContext context = serverRuntime.getContext();
                RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc").get();

                // ---------------------------------

                pkgOrchestrationService.importFrom(
                        context,
                        repositorySource.getObjectId(),
                        inputPackage,
                        true); // <--- NOTE

                // ---------------------------------

                context.commitChanges();
            }

            // check the length on that package is there and is correct.

            {
                ObjectContext context = serverRuntime.getContext();
                org.haikuos.haikudepotserver.dataobjects.Pkg pkg = org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context,"testpkg").get();
                org.haikuos.haikudepotserver.dataobjects.PkgVersion pkgVersion = pkgOrchestrationService.getLatestPkgVersionForPkg(
                        context,
                        pkg,
                        Repository.getByCode(context, "testrepo").get(),
                        Collections.singletonList(
                                Architecture.getByCode(context, "x86").get()
                        )).get();

                Assertions.assertThat(pkgVersion.getPayloadLength()).isEqualTo(expectedPayloadLength);
            }
        }
        finally {
            if(null!=repositoryDirectory) {
                FileHelper.delete(repositoryDirectory);
            }
        }
    }

}
