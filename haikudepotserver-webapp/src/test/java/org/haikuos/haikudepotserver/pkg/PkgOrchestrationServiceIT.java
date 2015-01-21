/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.io.Files;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.support.FileHelper;
import org.haikuos.pkg.model.Pkg;
import org.haikuos.pkg.model.PkgArchitecture;
import org.haikuos.pkg.model.PkgVersion;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.File;
import java.util.Collections;
import java.util.Random;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgOrchestrationServiceIT extends AbstractIntegrationTest {

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    private org.haikuos.pkg.model.Pkg createPkg(String minor) {
        org.haikuos.pkg.model.Pkg pkg = new Pkg();
        pkg.setArchitecture(PkgArchitecture.X86);
        pkg.setDescription("test-description-en");
        pkg.setSummary("test-summary-en");
        pkg.setName("testpkg");
        pkg.setVersion(new PkgVersion("1", minor, "3", "4", 5));
        return pkg;
    }

    private void importTestPackageWithMinor(String minor) {
        ObjectContext context = serverRuntime.getContext();
        Repository repository = Repository.getByCode(context, "testrepository").get();
        org.haikuos.pkg.model.Pkg pkg = createPkg(minor);

        pkgOrchestrationService.importFrom(
                context,
                repository.getObjectId(),
                pkg,
                false);

        context.commitChanges();
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
                Repository repository = Repository.getByCode(context, "testrepository").get();
                repositoryDirectory = new File(repository.getPackagesBaseURL().getPath());

                if (!repositoryDirectory.mkdirs()) {
                    throw new IllegalStateException("unable to create the on-disk repository");
                }

                Random random = new Random(System.currentTimeMillis());
                File fileF = new File(repositoryDirectory, "testpkg-1.3.3.4-5-x86.hpkg");
                byte[] buffer = new byte[1000 + (Math.abs(random.nextInt()) % 10*1000)];
                Files.write(buffer,fileF);
                expectedPayloadLength = buffer.length;
            }

            // now load the next package version in

            {
                ObjectContext context = serverRuntime.getContext();
                Repository repository = Repository.getByCode(context, "testrepository").get();

                // ---------------------------------

                pkgOrchestrationService.importFrom(
                        context,
                        repository.getObjectId(),
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

    /**
     * <p>Given a series of imported packages, the system will try to replicate any localizations sensibly.  This test
     * will check to make sure this works.</p>
     */

    @Test
    public void testImportFrom_replicateLocalizationIfEnglishMatches() {

        // create the repository

        integrationTestSupportService.createStandardTestData();

        // import the first version

        importTestPackageWithMinor("2");

        // now configure another language.

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg = org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context,"testpkg").get();
            org.haikuos.haikudepotserver.dataobjects.PkgVersion pkgVersion = pkgOrchestrationService.getLatestPkgVersionForPkg(
                    context,
                    pkg,
                    Collections.singletonList(
                            Architecture.getByCode(context, "x86").get()
                    )).get();

            pkgOrchestrationService.updatePkgVersionLocalization(
                            context,
                            pkgVersion,
                            NaturalLanguage.getByCode(context, NaturalLanguage.CODE_GERMAN).get(),
                            "test-summary-de",
                            "test-description-de");

            context.commitChanges();
        }

        // now load the next package version in

        importTestPackageWithMinor("3");

        // the second package should have the same german localization

        {
            ObjectContext context = serverRuntime.getContext();
            org.haikuos.haikudepotserver.dataobjects.Pkg pkg = org.haikuos.haikudepotserver.dataobjects.Pkg.getByName(context,"testpkg").get();
            org.haikuos.haikudepotserver.dataobjects.PkgVersion pkgVersion = pkgOrchestrationService.getLatestPkgVersionForPkg(
                    context,
                    pkg,
                    Collections.singletonList(
                            Architecture.getByCode(context, "x86").get()
                    )).get();

            Assertions.assertThat(pkgVersion.getMajor()).isEqualTo("1");
            Assertions.assertThat(pkgVersion.getMinor()).isEqualTo("3");

            PkgVersionLocalization localization = pkgVersion.getPkgVersionLocalization(NaturalLanguage.CODE_GERMAN).get();

            Assertions.assertThat(localization.getSummary()).isEqualTo("test-summary-de");
            Assertions.assertThat(localization.getDescription()).isEqualTo("test-description-de");
        }

    }

}
