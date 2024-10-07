/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SortOrder;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgSupplementModification;
import org.haiku.haikudepotserver.pkg.model.NonUserPkgSupplementModificationAgent;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;

import java.util.List;

@ContextConfiguration(classes = TestConfig.class)
public class PkgLocalizationServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    /**
     * <p>When a "_devel" package exists and an update is made to the localization of the parent
     * package then the localization should flow down to the "_devel" package too.</p>
     */

    @Test
    public void testUpdatePkgLocalization_develPkgHandling() {

        // setup the two packages.

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1Devel = context.newObject(org.haiku.haikudepotserver.dataobjects.Pkg.class);
            pkg1Devel.setActive(true);
            pkg1Devel.setIsNativeDesktop(false);
            pkg1Devel.setName("pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT);
            pkg1Devel.setPkgSupplement(PkgSupplement.getByBasePkgName(context, "pkg1"));
            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            NaturalLanguage naturalLanguageGerman = NaturalLanguage.getByCode(context, NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN);

            // ---------------------------------
            pkgLocalizationService.updatePkgLocalization(
                    context,
                    new NonUserPkgSupplementModificationAgent("cyril", "test"),
                    pkg1.getPkgSupplement(),
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
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(
                            context, "pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT);

            PkgLocalization pkgLocalization = pkg1Devel.getPkgSupplement().getPkgLocalization(
                    NaturalLanguageCoordinates.fromCode(NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN)).get();

            Assertions.assertThat(pkgLocalization.getTitle()).isEqualTo("title_kokako");
            Assertions.assertThat(pkgLocalization.getSummary()).isEqualTo("summary_kokako");
            Assertions.assertThat(pkgLocalization.getDescription()).isEqualTo("description_kokako");

            List<PkgSupplementModification> pkgSupplementModifications = ObjectSelect.query(PkgSupplementModification.class)
                    .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkg1Devel.getPkgSupplement()))
                    .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                    .select(context);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(1);

            PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.getLast();
            Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("cyril");
            Assertions.assertThat(pkgSupplementModification.getUser()).isNull();
            Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("test");
            Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo("""
changing localization for pkg [pkg1] in natural language [de];
title: [title_kokako]
summary: [summary_kokako]
description: [description_kokako]""");

        }

    }

}
