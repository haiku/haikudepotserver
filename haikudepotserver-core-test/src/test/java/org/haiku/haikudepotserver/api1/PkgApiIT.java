/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api1.model.PkgVersionType;
import org.haiku.haikudepotserver.api1.model.pkg.ConfigurePkgIconRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgIconsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgIconsResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgLocalizationsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgLocalizationsResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgScreenshotRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgScreenshotResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgScreenshotsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgScreenshotsResult;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgVersionLocalizationsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgVersionLocalizationsResult;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterResult;
import org.haiku.haikudepotserver.api1.model.pkg.PkgLocalization;
import org.haiku.haikudepotserver.api1.model.pkg.PkgScreenshot;
import org.haiku.haikudepotserver.api1.model.pkg.RemovePkgIconRequest;
import org.haiku.haikudepotserver.api1.model.pkg.RemovePkgScreenshotRequest;
import org.haiku.haikudepotserver.api1.model.pkg.ReorderPkgScreenshotsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haiku.haikudepotserver.api1.model.pkg.SearchPkgsResult;
import org.haiku.haikudepotserver.api1.model.pkg.UpdatePkgCategoriesRequest;
import org.haiku.haikudepotserver.api1.model.pkg.UpdatePkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.UpdatePkgLocalizationRequest;
import org.haiku.haikudepotserver.api1.model.pkg.UpdatePkgProminenceRequest;
import org.haiku.haikudepotserver.api1.model.pkg.UpdatePkgVersionRequest;
import org.haiku.haikudepotserver.api1.support.BadPkgIconException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgPkgCategory;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@ContextConfiguration(classes = TestConfig.class)
public class PkgApiIT extends AbstractIntegrationTest {

    @Resource
    private PkgApi pkgApi;

    @Test
    public void testUpdatePkgCategories() {

        setAuthenticatedUserToRoot();
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // setup some categories as a start condition.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, data.pkg1.getName());

            {
                PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "games"));
                pkg.getPkgSupplement().addToManyTarget(PkgSupplement.PKG_PKG_CATEGORIES.getName(), pkgPkgCategory, true);
            }

            {
                PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "business"));
                pkg.getPkgSupplement().addToManyTarget(PkgSupplement.PKG_PKG_CATEGORIES.getName(), pkgPkgCategory, true);
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
            ObjectContext context = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(context, data.pkg1.getName()).getPkgSupplement();

            Assertions.assertThat(ImmutableSet.of("business", "development")).isEqualTo(
                    pkgSupplementAfter.getPkgPkgCategories()
                            .stream()
                            .map(ppc -> ppc.getPkgCategory().getCode())
                            .collect(Collectors.toSet())
            );
        }

    }

    @Test
    public void searchPkgsTest() {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCode = "x86_64";
        request.naturalLanguageCode = NaturalLanguage.CODE_ENGLISH;
        request.repositoryCodes = Collections.singletonList("testrepo");
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
    public void searchPkgsTest_localizationDescriptionNotEnglish_hit() {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCode = "x86_64";
        request.repositoryCodes = Collections.singletonList("testrepo");
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
     * <p>This test checks where the client is searching for a package in a specific language, but
     * there is no localization for that specific language.  In this case, </p>
     */

    @Test
    public void searchPkgsTest_localizationDescriptionNotEnglishFallBackToEnglish_hit() {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCode = "x86_64";
        request.naturalLanguageCode = NaturalLanguage.CODE_FRENCH;
        request.repositoryCodes = Collections.singletonList("testrepo");
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
    public void testGetPkg_found_specific() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86_64";
        request.name = "pkg1";
        request.repositorySourceCode = "testreposrc_xyz";
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
        Assertions.assertThat(result.versions.get(0).architectureCode).isEqualTo("x86_64");
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
    public void testGetPkg_found_latest() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86_64";
        request.name = "pkg1";
        request.repositorySourceCode = "testreposrc_xyz";
        request.versionType = PkgVersionType.LATEST;
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        GetPkgResult result = pkgApi.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.name).isEqualTo("pkg1");
        Assertions.assertThat(result.versions.size()).isEqualTo(1);
        Assertions.assertThat(result.versions.get(0).architectureCode).isEqualTo("x86_64");
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
        request.architectureCode = "x86_64";
        request.name = "pkg9";
        request.versionType = PkgVersionType.LATEST;
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;
        request.repositorySourceCode = "testreposrc_xyz";

        try {

            // ------------------------------------
            pkgApi.getPkg(request);
            // ------------------------------------

            org.junit.jupiter.api.Assertions.fail("expected an instance of " + ObjectNotFoundException.class.getSimpleName() + " to be thrown, but was not");
        }
        catch(ObjectNotFoundException onfe) {
            Assertions.assertThat(onfe.getEntityName()).isEqualTo(Pkg.class.getSimpleName());
            Assertions.assertThat(onfe.getIdentifier()).isEqualTo("pkg9");
        }
        catch(Throwable th) {
            org.junit.jupiter.api.Assertions.fail("expected an instance of "+ObjectNotFoundException.class.getSimpleName()+" to be thrown, but "+th.getClass().getSimpleName()+" was instead");
        }
    }

    @Test
    public void testGetPkgIcons() {

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

        byte[] sampleHvif = getResourceData("sample.hvif");

        ConfigurePkgIconRequest request = new ConfigurePkgIconRequest();

        request.pkgName = "pkg1";
        request.pkgIcons = ImmutableList.of(
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        16,
                        Base64.getEncoder().encodeToString(sampleHvif)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        32,
                        Base64.getEncoder().encodeToString(sampleHvif)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        64,
                        Base64.getEncoder().encodeToString(sampleHvif)));

        try {

            // ------------------------------------
            pkgApi.configurePkgIcon(request);
            // ------------------------------------

            org.junit.jupiter.api.Assertions.fail("expected an instance of '"+BadPkgIconException.class.getSimpleName()+"' to have been thrown");

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
    public void testConfigurePkgIcon_ok_bitmap() throws Exception {

        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        byte[] sample16 = getResourceData("sample-16x16.png");
        byte[] sample32 = getResourceData("sample-32x32.png");
        byte[] sample64 = getResourceData("sample-64x64.png");

        ConfigurePkgIconRequest request = new ConfigurePkgIconRequest();

        request.pkgName = "pkg1";
        request.pkgIcons = ImmutableList.of(
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        16,
                        Base64.getEncoder().encodeToString(sample16)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        32,
                        Base64.getEncoder().encodeToString(sample32)),
                new ConfigurePkgIconRequest.PkgIcon(
                        MediaType.PNG.toString(),
                        64,
                        Base64.getEncoder().encodeToString(sample64)));

        // ------------------------------------
        pkgApi.configurePkgIcon(request);
        // ------------------------------------

        {
            ObjectContext objectContext = serverRuntime.newContext();
            Pkg pkgAfter = Pkg.getByName(objectContext, "pkg1");
            PkgSupplement pkgSupplementAfter = pkgAfter.getPkgSupplement();

            org.haiku.haikudepotserver.dataobjects.MediaType mediaTypePng
                    = org.haiku.haikudepotserver.dataobjects.MediaType.getByCode(
                    objectContext,
                    MediaType.PNG.toString());

            Assertions.assertThat(pkgSupplementAfter.getPkgIcons().size()).isEqualTo(3);

            PkgIcon pkgIcon16 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 16);
            Assertions.assertThat(pkgIcon16.getPkgIconImage().getData()).isEqualTo(sample16);

            PkgIcon pkgIcon32 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 32);
            Assertions.assertThat(pkgIcon32.getPkgIconImage().getData()).isEqualTo(sample32);

            PkgIcon pkgIcon64 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 64);
            Assertions.assertThat(pkgIcon64.getPkgIconImage().getData()).isEqualTo(sample64);
        }
    }


    /**
     * <p>This test will configure the icons for the package.</p>
     */

    @Test
    public void testConfigurePkgIcon_ok_hvif() throws Exception {

        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        byte[] sampleHvif = getResourceData("sample.hvif");

        ConfigurePkgIconRequest request = new ConfigurePkgIconRequest();

        request.pkgName = "pkg1";
        request.pkgIcons = Collections.singletonList(
                new ConfigurePkgIconRequest.PkgIcon(
                        org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE,
                        null,
                        Base64.getEncoder().encodeToString(sampleHvif)));

        // ------------------------------------
        pkgApi.configurePkgIcon(request);
        // ------------------------------------

        {
            ObjectContext objectContext = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(objectContext, "pkg1").getPkgSupplement();

            org.haiku.haikudepotserver.dataobjects.MediaType mediaTypeHvif
                    = org.haiku.haikudepotserver.dataobjects.MediaType.getByCode(
                    objectContext,
                    org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE);

            Assertions.assertThat(pkgSupplementAfter.getPkgIcons().size()).isEqualTo(1);

            PkgIcon pkgIconHvif = pkgSupplementAfter.getPkgIcon(mediaTypeHvif, null);
            Assertions.assertThat(pkgIconHvif.getPkgIconImage().getData()).isEqualTo(sampleHvif);
        }
    }

    /**
     * <p>This test knows that an icon exists for pkg1 and then removes it.</p>
     */

    @Test
    public void testRemoveIcon() {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext objectContext = serverRuntime.newContext();
            PkgSupplement pkgSupplementBefore = Pkg.getByName(objectContext, "pkg1").getPkgSupplement();
            Assertions.assertThat(pkgSupplementBefore.getPkgIcons().size()).isEqualTo(3); // 16 and 32 px sizes + hvif
        }

        // ------------------------------------
        pkgApi.removePkgIcon(new RemovePkgIconRequest("pkg1"));
        // ------------------------------------

        {
            ObjectContext objectContext = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(objectContext, "pkg1").getPkgSupplement();
            Assertions.assertThat(pkgSupplementAfter.getPkgIcons().size()).isEqualTo(0);
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshots() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        GetPkgScreenshotsResult result = pkgApi.getPkgScreenshots(new GetPkgScreenshotsRequest(data.pkg1.getName()));
        // ------------------------------------

        PkgSupplement pkgSupplement = data.pkg1.getPkgSupplement();
        Assertions.assertThat(result.items.size()).isEqualTo(pkgSupplement.getPkgScreenshots().size());
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshots = pkgSupplement.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshots).hasSize(3);

        int[] widths = { 320, 240, 320 };
        int[] heights = { 240, 320, 240 };
        int[] lengths = { 41296, 28303, 33201 };

        for(int i=0;i<sortedScreenshots.size();i++) {
            org.haiku.haikudepotserver.dataobjects.PkgScreenshot pkgScreenshot = sortedScreenshots.get(i);
            PkgScreenshot apiPkgScreenshot = result.items.get(i);

            Assertions.assertThat(pkgScreenshot.getCode()).isEqualTo(apiPkgScreenshot.code);
            Assertions.assertThat(pkgScreenshot.getWidth()).isEqualTo(apiPkgScreenshot.width);
            Assertions.assertThat(pkgScreenshot.getHeight()).isEqualTo(apiPkgScreenshot.height);
            Assertions.assertThat(pkgScreenshot.getLength()).isEqualTo(apiPkgScreenshot.length);

            Assertions.assertThat(apiPkgScreenshot.width).isEqualTo(widths[i]);
            Assertions.assertThat(apiPkgScreenshot.height).isEqualTo(heights[i]);
            Assertions.assertThat(apiPkgScreenshot.length).isEqualTo(lengths[i]);
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshot() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        String code = data.pkg1.getPkgSupplement().getSortedPkgScreenshots().get(0).getCode();

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
    public void testRemovePkgScreenshot() {
        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshotsBefore
                = data.pkg1.getPkgSupplement().getSortedPkgScreenshots();

        if(sortedScreenshotsBefore.size() < 2) {
            throw new IllegalStateException("the test cannot run without more than two screenshots");
        }

        final String code1 = sortedScreenshotsBefore.get(1).getCode();

        // ------------------------------------
        pkgApi.removePkgScreenshot(new RemovePkgScreenshotRequest(code1));
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        PkgSupplement pkgSupplementAfter = Pkg.getByName(context, data.pkg1.getName()).getPkgSupplement();
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshotsAfter
                = pkgSupplementAfter.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(sortedScreenshotsBefore.size()-1);
        Assertions.assertThat(sortedScreenshotsAfter.stream().anyMatch(s -> s.getCode().equals(code1))).isFalse();
    }

    /**
     * <p>This test assumes that the test data has a pkg1 with three screenshots associated with it.</p>
     */

    @Test
    public void testReorderPkgScreenshots() {
        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshotsBefore
                = data.pkg1.getPkgSupplement().getSortedPkgScreenshots();

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

        ObjectContext context = serverRuntime.newContext();
        PkgSupplement pkgSupplement = Pkg.getByName(context, data.pkg1.getName()).getPkgSupplement();
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshotsAfter
                = pkgSupplement.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(3);
        Assertions.assertThat(sortedScreenshotsAfter.get(0).getCode()).isEqualTo(sortedScreenshotsBefore.get(2).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(1).getCode()).isEqualTo(sortedScreenshotsBefore.get(0).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(2).getCode()).isEqualTo(sortedScreenshotsBefore.get(1).getCode());
    }

    @Test
    public void testUpdatePkgLocalization() {
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
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            List<String[]> rules = ImmutableList.of(
                    new String[] { NaturalLanguage.CODE_ENGLISH, "flourescence" },
                    new String[] { NaturalLanguage.CODE_FRENCH, "treacle" },
                    new String[] { NaturalLanguage.CODE_GERMAN, "Packet 1" }
            );

            for(String[] rule : rules) {
                Assertions.assertThat(
                        org.haiku.haikudepotserver.dataobjects.PkgLocalization.getForPkgAndNaturalLanguageCode(context, pkg1, rule[0]).get().getTitle()
                ).isEqualTo(rule[1]);
            }
        }

    }

    /**
     * <p>This test checks that it is possible to get a couple of known localizations for a known pkg.</p>
     */

    @Test
    public void testGetPkgLocalizations() {
        integrationTestSupportService.createStandardTestData();

        GetPkgLocalizationsRequest request = new GetPkgLocalizationsRequest();
        request.naturalLanguageCodes = ImmutableList.of(NaturalLanguage.CODE_ENGLISH, NaturalLanguage.CODE_GERMAN);
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgLocalizationsResult result = pkgApi.getPkgLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.pkgLocalizations.size()).isEqualTo(2);

        PkgLocalization en = result.pkgLocalizations.stream().filter(l -> l.naturalLanguageCode.equals(NaturalLanguage.CODE_ENGLISH)).findFirst().get();
        PkgLocalization de = result.pkgLocalizations.stream().filter(l -> l.naturalLanguageCode.equals(NaturalLanguage.CODE_GERMAN)).findFirst().get();
        Assertions.assertThat(en.title).isEqualTo("Package 1");
        Assertions.assertThat(de.title).isEqualTo("Packet 1");
    }

    /**
     * <p>This test requests german and english, but only english is present so needs to check that the output
     * contains only the english data.</p>
     */

    @Test
    public void testGetPkgVersionLocalizations() {
        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        GetPkgVersionLocalizationsRequest request = new GetPkgVersionLocalizationsRequest();
        request.architectureCode = "x86_64";
        request.repositorySourceCode = "testreposrc_xyz";
        request.naturalLanguageCodes = ImmutableList.of(NaturalLanguage.CODE_ENGLISH, NaturalLanguage.CODE_GERMAN);
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgVersionLocalizationsResult result = pkgApi.getPkgVersionLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.pkgVersionLocalizations.size()).isEqualTo(1);
        Assertions.assertThat(result.pkgVersionLocalizations.get(0).description).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.pkgVersionLocalizations.get(0).summary).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testUpdatePkgProminence() {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        UpdatePkgProminenceRequest request = new UpdatePkgProminenceRequest();
        request.pkgName = "pkg1";
        request.prominenceOrdering = 200;
        request.repositoryCode = "testrepo";

        // ------------------------------------
        pkgApi.updatePkgProminence(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            Repository repository = Repository.getByCode(context, "testrepo");
            Assertions.assertThat(pkg1.getPkgProminence(repository).get().getProminence().getOrdering()).isEqualTo(200);
        }

    }

    @Test
    public void testGetPkgChangelog() {
        integrationTestSupportService.createStandardTestData();

        GetPkgChangelogRequest request = new GetPkgChangelogRequest();
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgChangelogResult result = pkgApi.getPkgChangelog(request);
        // ------------------------------------

        Assertions.assertThat(result.content).isEqualTo("Stadt\nKarlsruhe");
    }

    /**
     * <p>This will override the change log that was there with the new value.</p>
     */

    @Test
    public void testUpdatePkgChangelog_withContent() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdatePkgChangelogRequest request = new UpdatePkgChangelogRequest();
        request.pkgName = "pkg1";
        request.content = "  das Zimmer  ";

        // ------------------------------------
        pkgApi.updatePkgChangelog(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(context, "pkg1").getPkgSupplement();
            Assertions.assertThat(pkgSupplementAfter.getPkgChangelog().get().getContent()).isEqualTo("das Zimmer");
        }

    }

    /**
     * <p>Writing in no content will mean that the change log that was there is now removed.</p>
     */

    @Test
    public void testUpdatePkgChangelog_withNoContent() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdatePkgChangelogRequest request = new UpdatePkgChangelogRequest();
        request.pkgName = "pkg1";
        request.content = "";

        // ------------------------------------
        pkgApi.updatePkgChangelog(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(context, "pkg1").getPkgSupplement();
            Assertions.assertThat(pkgSupplementAfter.getPkgChangelog().isPresent()).isFalse();
        }

    }

    @Test
    public void updatePkgVersion_deactivate() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdatePkgVersionRequest request = new UpdatePkgVersionRequest();
        request.pkgName = "pkg1";
        request.repositorySourceCode = "testreposrc_xyz";
        request.architectureCode = "x86_64";
        request.major = "1";
        request.micro = "2";
        request.revision = 3;

        request.filter = Collections.singletonList(UpdatePkgVersionRequest.Filter.ACTIVE);
        request.active = false;

        // ------------------------------------
        pkgApi.updatePkgVersion(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            Architecture architecture = Architecture.getByCode(context, "x86_64");
            PkgVersion pkgVersion = PkgVersion.getForPkg(context, pkg1, repositorySource, architecture, new VersionCoordinates("1",null,"2",null,3)).get();
            Assertions.assertThat(pkgVersion.getActive()).isFalse();
        }
    }

    @Test
    public void testIncrementViewCounter() {
        integrationTestSupportService.createStandardTestData();

        IncrementViewCounterRequest request = new IncrementViewCounterRequest();
        request.major = "1";
        request.micro = "2";
        request.revision = 3;
        request.name = "pkg1";
        request.architectureCode = "x86_64";
        request.repositoryCode = "testrepo";

        // ------------------------------------
        IncrementViewCounterResult result = pkgApi.incrementViewCounter(request);
        // ------------------------------------

        Assertions.assertThat(result).isNotNull();

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            Architecture architecture = Architecture.getByCode(context, "x86_64");
            PkgVersion pkgVersion = PkgVersion.getForPkg(context, pkg1, repositorySource, architecture, new VersionCoordinates("1",null,"2",null,3)).get();
            Assertions.assertThat(pkgVersion.getViewCounter()).isEqualTo(1L);
        }

    }

}
