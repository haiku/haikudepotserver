/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import com.googlecode.jsonrpc4j.Base64;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.model.PkgVersionType;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.model.pkg.PkgLocalization;
import org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionLocalization;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.LimitExceededException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.PkgScreenshot;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgApiIT extends AbstractIntegrationTest {

    @Resource
    PkgApi pkgApi;

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    @Resource
    PkgOrchestrationService pkgService;

    @Test
    public void testUpdatePkgCategories() throws Exception {

        setAuthenticatedUserToRoot();
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // setup some categories as a start condition.

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg = Pkg.getByName(context, data.pkg1.getName()).get();

            {
                PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "games").get());
                pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            }

            {
                PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "business").get());
                pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            }

            context.commitChanges();
        }

        UpdatePkgCategoriesRequest request = new UpdatePkgCategoriesRequest();
        request.pkgName = data.pkg1.getName();
        request.pkgCategoryCodes = ImmutableList.of("business", "development");

        // ------------------------------------
        pkgApi.updatePkgCategories(request);
        // ------------------------------------

        // now we need to check on those categories.  GAMES should have gone, BUSINESS should remain
        // and DEVELOPMENT should be added.

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg = Pkg.getByName(context, data.pkg1.getName()).get();

            Assertions.assertThat(ImmutableSet.of("business", "development")).isEqualTo(
                    ImmutableSet.copyOf(Iterables.transform(
                            pkg.getPkgPkgCategories(),
                            new Function<PkgPkgCategory, String>() {
                                @Override
                                public String apply(PkgPkgCategory input) {
                                    return input.getPkgCategory().getCode();
                                }
                            }
                    ))
            );
        }

    }

    @Test
    public void searchPkgsTest() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCodes = ImmutableList.of("any","x86");
        request.naturalLanguageCode = NaturalLanguage.CODE_ENGLISH;
        request.expression = "pk";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchPkgsResult result = pkgApi.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.total).isEqualTo(4); // note includes the "any" package
        Assertions.assertThat(result.items.size()).isEqualTo(2);
        Assertions.assertThat(result.items.get(0).name).isEqualTo("pkg1");
        Assertions.assertThat(result.items.get(1).name).isEqualTo("pkg2");
    }

    /**
     * <p>This test will check that the search is able to find text in the content of the package
     * version localization where the localization is a specific language other than english.
     * This test will find something because it is looking for spanish and has some text from the
     * spanish localization for the package version.</p>
     */

    @Test
    public void searchPkgsTest_localizationDescriptionNotEnglish_hit() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCodes = Collections.singletonList("x86");
        request.naturalLanguageCode = NaturalLanguage.CODE_SPANISH;
        request.expression = "feij";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchPkgsResult result = pkgApi.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.total).isEqualTo(1);
        Assertions.assertThat(result.items.size()).isEqualTo(1);
        Assertions.assertThat(result.items.get(0).name).isEqualTo("pkg1");
        Assertions.assertThat(result.items.get(0).versions.get(0).title).isEqualTo("Ping 1");
        Assertions.assertThat(result.items.get(0).versions.get(0).summary).isEqualTo("pkg1Version2SummarySpanish_feijoa");
    }

    /**
     * <p>This test will check that the search is able to find text in the content of the package
     * version localization where the localization is a specific language other than english.  This
     * test will not find anything because it is looking for a keyword in the english test, but is
     * searching for spanish.</p>
     */

    @Test
    public void searchPkgsTest_localizationDescriptionNotEnglish_noHit() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCodes = Collections.singletonList("x86");
        request.naturalLanguageCode = NaturalLanguage.CODE_SPANISH;
        request.expression = "persim";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchPkgsResult result = pkgApi.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.total).isEqualTo(0);
        Assertions.assertThat(result.items.size()).isEqualTo(0);
    }

    /**
     * <p>This test checks where the client is searching for a package in a specific language, but
     * there is no localization for that specific language.  In this case, </p>
     * @throws ObjectNotFoundException
     */

    @Test
    public void searchPkgsTest_localizationDescriptionNotEnglishFallBackToEnglish_hit() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCodes = Collections.singletonList("x86");
        request.naturalLanguageCode = NaturalLanguage.CODE_FRENCH;
        request.expression = "persimon";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchPkgsResult result = pkgApi.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.total).isEqualTo(1);
        Assertions.assertThat(result.items.size()).isEqualTo(1);
        Assertions.assertThat(result.items.get(0).name).isEqualTo("pkg1");
        Assertions.assertThat(result.items.get(0).versions.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testGetPkg_found_specific() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86";
        request.name = "pkg1";
        request.versionType = PkgVersionType.SPECIFIC;
        request.naturalLanguageCode = NaturalLanguage.CODE_ENGLISH;
        request.major = "1";
        request.micro = "2";
        request.minor = null;
        request.preRelease = null;
        request.revision = 4;

        // ------------------------------------
        GetPkgResult result = pkgApi.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.name).isEqualTo("pkg1");
        Assertions.assertThat(result.versions.size()).isEqualTo(1);
        Assertions.assertThat(result.versions.get(0).title).isEqualTo("Package 1");
        Assertions.assertThat(result.versions.get(0).architectureCode).isEqualTo("x86");
        Assertions.assertThat(result.versions.get(0).major).isEqualTo("1");
        Assertions.assertThat(result.versions.get(0).micro).isEqualTo("2");
        Assertions.assertThat(result.versions.get(0).revision).isEqualTo(4);
        Assertions.assertThat(result.versions.get(0).description).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.versions.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    /**
     * <p>In this test, an German localization is requested, but there is no localization present for German so it will
     * fall back English.</p>
     */

    @Test
    public void testGetPkg_found_latest() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86";
        request.name = "pkg1";
        request.versionType = PkgVersionType.LATEST;
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        GetPkgResult result = pkgApi.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.name).isEqualTo("pkg1");
        Assertions.assertThat(result.versions.size()).isEqualTo(1);
        Assertions.assertThat(result.versions.get(0).architectureCode).isEqualTo("x86");
        Assertions.assertThat(result.versions.get(0).major).isEqualTo("1");
        Assertions.assertThat(result.versions.get(0).micro).isEqualTo("2");
        Assertions.assertThat(result.versions.get(0).revision).isEqualTo(4);
        Assertions.assertThat(result.versions.get(0).description).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.versions.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testGetPkg_notFound() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86";
        request.name = "pkg9";
        request.versionType = PkgVersionType.LATEST;
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        try {

            // ------------------------------------
            pkgApi.getPkg(request);
            // ------------------------------------

            Assert.fail("expected an instance of " + ObjectNotFoundException.class.getSimpleName() + " to be thrown, but was not");
        }
        catch(ObjectNotFoundException onfe) {
            Assertions.assertThat(onfe.getEntityName()).isEqualTo(Pkg.class.getSimpleName());
            Assertions.assertThat(onfe.getIdentifier()).isEqualTo("pkg9");
        }
        catch(Throwable th) {
            Assert.fail("expected an instance of "+ObjectNotFoundException.class.getSimpleName()+" to be thrown, but "+th.getClass().getSimpleName()+" was instead");
        }
    }

    @Test
    public void testGetPkgIcons() throws Exception {

        integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        GetPkgIconsResult result = pkgApi.getPkgIcons(new GetPkgIconsRequest("pkg1"));
        // ------------------------------------

        Assertions.assertThat(result.pkgIcons.size()).isEqualTo(3);
        // check more stuff...

    }

    /**
     * <p>Here we are trying to load the HVIF data in as PNG images.</p>
     */

    @Test
    public void testConfigurePkgIcon_badData() throws Exception {

        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        byte[] sampleHvif = getResourceData("/sample.hvif");

        ConfigurePkgIconRequest request = new ConfigurePkgIconRequest();

        request.pkgName = "pkg1";
        request.pkgIcons = ImmutableList.of(
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        16,
                        Base64.encodeBytes(sampleHvif)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        32,
                        Base64.encodeBytes(sampleHvif)),
                new ConfigurePkgIconRequest.PkgIcon(
                        org.haikuos.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE,
                        null,
                        Base64.encodeBytes(sampleHvif)));

        try {

            // ------------------------------------
            pkgApi.configurePkgIcon(request);
            // ------------------------------------

            Assert.fail("expected an instance of '"+BadPkgIconException.class.getSimpleName()+"' to have been thrown");

        }
        catch(BadPkgIconException bpie) {

            // This is the first one that failed so we should get this come up as the exception that was thrown.

            Assertions.assertThat(bpie.getSize()).isEqualTo(16);
            Assertions.assertThat(bpie.getMediaTypeCode()).isEqualTo(MediaType.PNG.toString());
        }
    }


    /**
     * <p>This test will configure the icons for the package.</p>
     */

    @Test
    public void testConfigurePkgIcon_ok() throws Exception {

        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        byte[] sample16 = getResourceData("/sample-16x16.png");
        byte[] sample32 = getResourceData("/sample-32x32.png");
        byte[] sampleHvif = getResourceData("/sample.hvif");

        ConfigurePkgIconRequest request = new ConfigurePkgIconRequest();

        request.pkgName = "pkg1";
        request.pkgIcons = ImmutableList.of(
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        16,
                        Base64.encodeBytes(sample16)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        32,
                        Base64.encodeBytes(sample32)),
                new ConfigurePkgIconRequest.PkgIcon(
                        org.haikuos.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE,
                        null,
                        Base64.encodeBytes(sampleHvif)));

        // ------------------------------------
        pkgApi.configurePkgIcon(request);
        // ------------------------------------

        {
            ObjectContext objectContext = serverRuntime.getContext();
            Optional<Pkg> pkgOptionalafter = Pkg.getByName(objectContext, "pkg1");

            org.haikuos.haikudepotserver.dataobjects.MediaType mediaTypePng
                    = org.haikuos.haikudepotserver.dataobjects.MediaType.getByCode(
                    objectContext,
                    MediaType.PNG.toString()).get();

            org.haikuos.haikudepotserver.dataobjects.MediaType mediaTypeHvif
                    = org.haikuos.haikudepotserver.dataobjects.MediaType.getByCode(
                    objectContext,
                    org.haikuos.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE).get();

            Assertions.assertThat(pkgOptionalafter.get().getPkgIcons().size()).isEqualTo(3);

            Optional<org.haikuos.haikudepotserver.dataobjects.PkgIcon> pkgIcon16Optional = pkgOptionalafter.get().getPkgIcon(mediaTypePng, 16);
            Assertions.assertThat(pkgIcon16Optional.get().getPkgIconImage().get().getData()).isEqualTo(sample16);

            Optional<org.haikuos.haikudepotserver.dataobjects.PkgIcon> pkgIcon32Optional = pkgOptionalafter.get().getPkgIcon(mediaTypePng, 32);
            Assertions.assertThat(pkgIcon32Optional.get().getPkgIconImage().get().getData()).isEqualTo(sample32);

            Optional<org.haikuos.haikudepotserver.dataobjects.PkgIcon> pkgIconHvifOptional = pkgOptionalafter.get().getPkgIcon(mediaTypeHvif, null);
            Assertions.assertThat(pkgIconHvifOptional.get().getPkgIconImage().get().getData()).isEqualTo(sampleHvif);
        }
    }

    /**
     * <p>This test knows that an icon exists for pkg1 and then removes it.</p>
     */

    @Test
    public void testRemoveIcon() throws Exception {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext objectContext = serverRuntime.getContext();
            Optional<Pkg> pkgOptionalBefore = Pkg.getByName(objectContext, "pkg1");
            Assertions.assertThat(pkgOptionalBefore.get().getPkgIcons().size()).isEqualTo(3); // 16 and 32 px sizes + hvif
        }

        // ------------------------------------
        pkgApi.removePkgIcon(new RemovePkgIconRequest("pkg1"));
        // ------------------------------------

        {
            ObjectContext objectContext = serverRuntime.getContext();
            Optional<Pkg> pkgOptionalBefore = Pkg.getByName(objectContext, "pkg1");
            Assertions.assertThat(pkgOptionalBefore.get().getPkgIcons().size()).isEqualTo(0);
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshots() throws ObjectNotFoundException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        GetPkgScreenshotsResult result = pkgApi.getPkgScreenshots(new GetPkgScreenshotsRequest(data.pkg1.getName()));
        // ------------------------------------

        Assertions.assertThat(result.items.size()).isEqualTo(data.pkg1.getPkgScreenshots().size());
        List<PkgScreenshot> sortedScreenshots = data.pkg1.getSortedPkgScreenshots();

        for(int i=0;i<sortedScreenshots.size();i++) {
            PkgScreenshot pkgScreenshot = sortedScreenshots.get(i);
            org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot apiPkgScreenshot = result.items.get(i);
            Assertions.assertThat(pkgScreenshot.getCode()).isEqualTo(apiPkgScreenshot.code);
            Assertions.assertThat(pkgScreenshot.getWidth()).isEqualTo(320);
            Assertions.assertThat(pkgScreenshot.getHeight()).isEqualTo(240);
            Assertions.assertThat(pkgScreenshot.getLength()).isEqualTo(41296);
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshot() throws ObjectNotFoundException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        String code = data.pkg1.getSortedPkgScreenshots().get(0).getCode();

        // ------------------------------------
        GetPkgScreenshotResult result = pkgApi.getPkgScreenshot(new GetPkgScreenshotRequest(code));
        // ------------------------------------

        Assertions.assertThat(result.code).isEqualTo(code);
        Assertions.assertThat(result.width).isEqualTo(320);
        Assertions.assertThat(result.height).isEqualTo(240);
        Assertions.assertThat(result.length).isEqualTo(41296);
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testRemovePkgScreenshot() throws Exception {
        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        List<PkgScreenshot> sortedScreenshotsBefore = data.pkg1.getSortedPkgScreenshots();

        if(sortedScreenshotsBefore.size() < 2) {
            throw new IllegalStateException("the test cannot run without more than two screenshots");
        }

        final String code1 = sortedScreenshotsBefore.get(1).getCode();

        // ------------------------------------
        pkgApi.removePkgScreenshot(new RemovePkgScreenshotRequest(code1));
        // ------------------------------------

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, data.pkg1.getName());
        List<PkgScreenshot> sortedScreenshotsAfter = pkgOptional.get().getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(sortedScreenshotsBefore.size()-1);

        Assertions.assertThat(Iterables.tryFind(sortedScreenshotsAfter, new Predicate<PkgScreenshot>() {
            @Override
            public boolean apply(PkgScreenshot pkgScreenshot) {
                return pkgScreenshot.getCode().equals(code1);
            }
        }).isPresent()).isFalse();
    }

    /**
     * <p>This test assumes that the test data has a pkg1 with three screenshots associated with it.</p>
     */

    @Test
    public void testReorderPkgScreenshots() throws Exception {
        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        List<PkgScreenshot> sortedScreenshotsBefore = data.pkg1.getSortedPkgScreenshots();

        if(3 != sortedScreenshotsBefore.size()) {
            throw new IllegalStateException("the test requires that pkg1 has three screenshots associated with it");
        }

        // ------------------------------------
        pkgApi.reorderPkgScreenshots(new ReorderPkgScreenshotsRequest(
                data.pkg1.getName(),
                ImmutableList.of(
                        sortedScreenshotsBefore.get(2).getCode(),
                        sortedScreenshotsBefore.get(0).getCode()
                )
        ));
        // ------------------------------------

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, data.pkg1.getName());
        List<PkgScreenshot> sortedScreenshotsAfter = pkgOptional.get().getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(3);
        Assertions.assertThat(sortedScreenshotsAfter.get(0).getCode()).isEqualTo(sortedScreenshotsBefore.get(2).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(1).getCode()).isEqualTo(sortedScreenshotsBefore.get(0).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(2).getCode()).isEqualTo(sortedScreenshotsBefore.get(1).getCode());
    }

    @Test
    public void testUpdatePkgLocalization() throws Exception {
        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        UpdatePkgLocalizationRequest request = new UpdatePkgLocalizationRequest();
        request.pkgName = "pkg1";
        request.pkgLocalizations = ImmutableList.of(
                new PkgLocalization(NaturalLanguage.CODE_ENGLISH, "flourescence", null, null),
                new PkgLocalization(NaturalLanguage.CODE_FRENCH, "treacle", null, null));

        // ------------------------------------
        pkgApi.updatePkgLocalization(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1").get();
            List<String[]> rules = ImmutableList.of(
                    new String[] { NaturalLanguage.CODE_ENGLISH, "flourescence" },
                    new String[] { NaturalLanguage.CODE_FRENCH, "treacle" },
                    new String[] { NaturalLanguage.CODE_GERMAN, "Packet 1" }
            );

            for(String[] rule : rules) {
                Assertions.assertThat(
                        org.haikuos.haikudepotserver.dataobjects.PkgLocalization.getForPkgAndNaturalLanguageCode(context, pkg1, rule[0]).get().getTitle()
                ).isEqualTo(rule[1]);
            }
        }

    }

    /**
     * <p>This test checks that it is possible to get a couple of known localizations for a known pkg.</p>
     */

    @Test
    public void testGetPkgLocalizations() throws Exception {
        integrationTestSupportService.createStandardTestData();

        GetPkgLocalizationsRequest request = new GetPkgLocalizationsRequest();
        request.naturalLanguageCodes = ImmutableList.of(NaturalLanguage.CODE_ENGLISH, NaturalLanguage.CODE_GERMAN);
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgLocalizationsResult result = pkgApi.getPkgLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.pkgLocalizations.size()).isEqualTo(2);

        PkgLocalization en = Iterables.find(result.pkgLocalizations, new Predicate<PkgLocalization>() {
            @Override
            public boolean apply(PkgLocalization input) {
                return input.naturalLanguageCode.equals(NaturalLanguage.CODE_ENGLISH);
            }
        });

        PkgLocalization de = Iterables.find(result.pkgLocalizations, new Predicate<PkgLocalization>() {
            @Override
            public boolean apply(PkgLocalization input) {
                return input.naturalLanguageCode.equals(NaturalLanguage.CODE_GERMAN);
            }
        });

        Assertions.assertThat(en.title).isEqualTo("Package 1");
        Assertions.assertThat(de.title).isEqualTo("Packet 1");
    }

    /**
     * <p>This test requests german and english, but only english is present so needs to check that the output
     * contains only the english data.</p>
     */

    @Test
    public void testGetPkgVersionLocalizations() throws Exception {
        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        GetPkgVersionLocalizationsRequest request = new GetPkgVersionLocalizationsRequest();
        request.architectureCode = "x86";
        request.naturalLanguageCodes = ImmutableList.of(NaturalLanguage.CODE_ENGLISH, NaturalLanguage.CODE_GERMAN);
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgVersionLocalizationsResult result = pkgApi.getPkgVersionLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.pkgVersionLocalizations.size()).isEqualTo(1);
        Assertions.assertThat(result.pkgVersionLocalizations.get(0).description).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.pkgVersionLocalizations.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    /**
     * <p>This test is just checking that if too many packages are requested that it throws the right
     * sort of exception.</p>
     */

    @Test
    public void testGetBulkPkg__limitExceeded() throws Exception {

        GetBulkPkgRequest request = new GetBulkPkgRequest();
        request.filter = ImmutableList.copyOf(GetBulkPkgRequest.Filter.values());
        request.versionType = PkgVersionType.LATEST;
        request.architectureCodes = Collections.singletonList("x86");
        request.naturalLanguageCode = "en";
        request.pkgNames = Lists.newArrayList();

        while(request.pkgNames.size() < PkgApi.GETBULKPKG_LIMIT + 1) {
            request.pkgNames.add("pkg");
        }

        try {
            // ------------------------------------
            pkgApi.getBulkPkg(request);
            // ------------------------------------
            Assert.fail("expected an instance of "+ LimitExceededException.class.getSimpleName()+" to be thrown");
        }
        catch(LimitExceededException lee) {
            // expected
        }
    }

    @Test
    public void testGetBulkPkg__ok() throws Exception {
        integrationTestSupportService.createStandardTestData();

        GetBulkPkgRequest request = new GetBulkPkgRequest();
        request.filter = ImmutableList.copyOf(GetBulkPkgRequest.Filter.values());
        request.versionType = PkgVersionType.LATEST;
        request.architectureCodes = ImmutableList.of("any","x86");
        request.naturalLanguageCode = "en";
        request.pkgNames = ImmutableList.of("pkg1","pkg2","pkg3","pkg4","pkgany"); // pkg4 does not exist

        // ------------------------------------
        GetBulkPkgResult result = pkgApi.getBulkPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.pkgs.size()).isEqualTo(4); // includes the any package

        // check they are all there.

        Set<String> packageNames = ImmutableSet.copyOf(Lists.transform(
                result.pkgs,
                new Function<GetBulkPkgResult.Pkg, String>() {
                    @Override
                    public String apply(GetBulkPkgResult.Pkg input) {
                        return input.name;
                    }
                }
        ));

        Assertions.assertThat(packageNames).containsOnly("pkg1","pkg2","pkg3","pkgany");

        // now check pkg1 because it has some in-depth data on it.

        GetBulkPkgResult.Pkg pkg1 = Iterables.tryFind(result.pkgs, new Predicate<GetBulkPkgResult.Pkg>() {
            @Override
            public boolean apply(GetBulkPkgResult.Pkg input) {
                return input.name.equals("pkg1");
            }
        }).get();

        Assertions.assertThat(pkg1.name).isEqualTo("pkg1");
        Assertions.assertThat(pkg1.modifyTimestamp).isNotNull();

        Assertions.assertThat(pkg1.pkgCategoryCodes.size()).isEqualTo(1);
        Assertions.assertThat(pkg1.pkgCategoryCodes.get(0)).isEqualTo("graphics");

        Assertions.assertThat(pkg1.derivedRating).isNotNull();
        Assertions.assertThat(pkg1.derivedRating).isGreaterThanOrEqualTo(0.0f);
        Assertions.assertThat(pkg1.derivedRating).isLessThanOrEqualTo(5.0f);

        // there are three screen-shots loaded, but they are all the same so we can just check that the first
        // one is correct.
        Assertions.assertThat(pkg1.pkgScreenshots.size()).isEqualTo(3);
        Assertions.assertThat(pkg1.pkgScreenshots.get(0).code).isNotNull();
        Assertions.assertThat(pkg1.pkgScreenshots.get(0).width).isEqualTo(320);
        Assertions.assertThat(pkg1.pkgScreenshots.get(0).height).isEqualTo(240);

        // basic check here to make sure that the HPKR data is able to be flagged as being there.
        Assertions.assertThat(pkg1.pkgIcons.size()).isEqualTo(3);
        Assertions.assertThat(
                Iterables.tryFind(pkg1.pkgIcons, new Predicate<org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon>() {
                    @Override
                    public boolean apply(org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon input) {
                        return input.mediaTypeCode.equals(org.haikuos.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE);
                    }
                }).isPresent()).isTrue();

        Assertions.assertThat(pkg1.versions.size()).isEqualTo(1);
        Assertions.assertThat(pkg1.versions.get(0).title).isEqualTo("Package 1");
        Assertions.assertThat(pkg1.versions.get(0).description).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(pkg1.versions.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
        Assertions.assertThat(pkg1.versions.get(0).major).isEqualTo("1");
        Assertions.assertThat(pkg1.versions.get(0).micro).isEqualTo("2");
        Assertions.assertThat(pkg1.versions.get(0).revision).isEqualTo(4);
        Assertions.assertThat(pkg1.versions.get(0).preRelease).isNull();
        Assertions.assertThat(pkg1.versions.get(0).minor).isNull();

    }

    @Test
    public void testUpdatePkgProminence() throws Exception {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        UpdatePkgProminenceRequest request = new UpdatePkgProminenceRequest();
        request.pkgName = "pkg1";
        request.prominenceOrdering = 200;

        // ------------------------------------
        pkgApi.updatePkgProminence(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1").get();
            Assertions.assertThat(pkg1.getProminence().getOrdering()).isEqualTo(200);
        }

    }

}