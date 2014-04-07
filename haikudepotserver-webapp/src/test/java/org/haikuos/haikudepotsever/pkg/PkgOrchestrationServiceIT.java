package org.haikuos.haikudepotsever.pkg;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.PkgRepositoryImportJob;
import org.haikuos.haikudepotsever.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.IntegrationTestSupportService;
import org.haikuos.pkg.model.Pkg;
import org.haikuos.pkg.model.PkgArchitecture;
import org.haikuos.pkg.model.PkgVersion;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.Collections;

public class PkgOrchestrationServiceIT extends AbstractIntegrationTest {

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    private void importTestPackageWithMinor(String minor) {
        ObjectContext context = serverRuntime.getContext();
        Repository repository = Repository.getByCode(context, "testrepository").get();

        org.haikuos.pkg.model.Pkg pkg = new Pkg();
        pkg.setArchitecture(PkgArchitecture.X86);
        pkg.setDescription("test-description-en");
        pkg.setSummary("test-summary-en");
        pkg.setName("testpkg");
        pkg.setVersion(new PkgVersion("1", minor, "3", "4", 5));

        pkgOrchestrationService.importFrom(
                context,
                repository.getObjectId(),
                pkg);

        context.commitChanges();
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
            org.haikuos.haikudepotserver.dataobjects.PkgVersion pkgVersion = org.haikuos.haikudepotserver.dataobjects.PkgVersion.getLatestForPkg(
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
            org.haikuos.haikudepotserver.dataobjects.PkgVersion pkgVersion = org.haikuos.haikudepotserver.dataobjects.PkgVersion.getLatestForPkg(
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
