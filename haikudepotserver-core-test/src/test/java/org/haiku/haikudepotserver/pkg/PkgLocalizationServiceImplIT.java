/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class PkgLocalizationServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    /**
     * <p>When a "_devel" package exists and an update is made to the localization of the parent
     * package then the localization should flow down to the "_devel" package too.</p>
     */

    @Test
    public void testUpdatePkgLocalization_develPkgHandling() throws Exception {

        // setup the two packages.

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1Devel = context.newObject(org.haiku.haikudepotserver.dataobjects.Pkg.class);
            pkg1Devel.setActive(true);
            pkg1Devel.setName("pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT);
            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.tryGetByName(context, "pkg1").get();
            NaturalLanguage naturalLanguageGerman = NaturalLanguage.getByCode(context, NaturalLanguage.CODE_GERMAN).get();

            // ---------------------------------
            pkgLocalizationService.updatePkgLocalization(
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
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1Devel =
                    org.haiku.haikudepotserver.dataobjects.Pkg.tryGetByName(
                            context,
                            "pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT).get();

            PkgLocalization pkgLocalization = pkg1Devel.getPkgLocalization(NaturalLanguage.CODE_GERMAN).get();

            Assertions.assertThat(pkgLocalization.getTitle()).isEqualTo("title_kokako");
            Assertions.assertThat(pkgLocalization.getSummary()).isEqualTo("summary_kokako (development files)");
            Assertions.assertThat(pkgLocalization.getDescription()).isEqualTo("description_kokako");
        }

    }

}
