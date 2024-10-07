/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.SortOrder;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.exception.BadPkgIconException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ContextConfiguration(classes = TestConfig.class)
public class PkgApiServiceIT extends AbstractIntegrationTest {

    @Resource
    private PkgApiService pkgApiService;

    @Test
    public void testUpdatePkgCategories() {

        setAuthenticatedUserToRoot();
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // setup some categories as a start condition noting that `pkg1` comes with the category "graphics" by default.

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

        UpdatePkgCategoriesRequestEnvelope request = new UpdatePkgCategoriesRequestEnvelope()
                .pkgName("pkg1")
                .pkgCategoryCodes(List.of("business", "development"));

        // not ideal, but ensure total ordering on the pkg supplement modifications results
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100));

        // ------------------------------------
        pkgApiService.updatePkgCategories(request);
        // ------------------------------------

        // now we need to check on those categories.  GAMES should have gone, BUSINESS should remain
        // and DEVELOPMENT should be added.

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, data.pkg1.getName());

            Assertions.assertThat(ImmutableSet.of("business", "development")).isEqualTo(
                    pkg.getPkgSupplement().getPkgPkgCategories()
                            .stream()
                            .map(ppc -> ppc.getPkgCategory().getCode())
                            .collect(Collectors.toSet())
            );

            List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg);
            Assertions.assertThat(modifications.size()).isGreaterThanOrEqualTo(1);

            PkgSupplementModification lastModification = modifications.getLast();
            Assertions.assertThat(lastModification.getUser().getNickname()).isEqualTo("root");

        }

    }

    @Test
    public void searchPkgsTest() {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequestEnvelope request = new SearchPkgsRequestEnvelope()
                .architectureCode("x86_64")
                .naturalLanguageCode("en")
                .repositoryCodes(List.of("testrepo"))
                .expression("pk")
                .expressionType(SearchPkgsRequestEnvelope.ExpressionTypeEnum.CONTAINS)
                .limit(2)
                .offset(0);

        // ------------------------------------
        SearchPkgsResult result = pkgApiService.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.getTotal()).isEqualTo(4); // note includes the "any" package
        Assertions.assertThat(result.getItems().size()).isEqualTo(2);
        Assertions.assertThat(result.getItems().get(0).getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getItems().get(1).getName()).isEqualTo("pkg2");
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

        SearchPkgsRequestEnvelope request = new SearchPkgsRequestEnvelope()
                .architectureCode("x86_64")
                .naturalLanguageCode("es")
                .repositoryCodes(List.of("testrepo"))
                .expression("feij")
                .expressionType(SearchPkgsRequestEnvelope.ExpressionTypeEnum.CONTAINS)
                .limit(2)
                .offset(0);

        // ------------------------------------
        SearchPkgsResult result = pkgApiService.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.getTotal()).isEqualTo(1);
        Assertions.assertThat(result.getItems().size()).isEqualTo(1);
        Assertions.assertThat(result.getItems().get(0).getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getItems().get(0).getVersions().get(0).getTitle()).isEqualTo("Ping 1");
        Assertions.assertThat(result.getItems().get(0).getVersions().get(0).getSummary()).isEqualTo("pkg1Version2SummarySpanish_feijoa");
    }

    /**
     * <p>This test checks where the client is searching for a package in a specific language, but
     * there is no localization for that specific language.  In this case, </p>
     */

    @Test
    public void searchPkgsTest_localizationDescriptionNotEnglishFallBackToEnglish_hit() {
        integrationTestSupportService.createStandardTestData();

        SearchPkgsRequestEnvelope request = new SearchPkgsRequestEnvelope()
                .architectureCode("x86_64")
                .naturalLanguageCode("fr")
                .repositoryCodes(List.of("testrepo"))
                .expression("persimon")
                .expressionType(SearchPkgsRequestEnvelope.ExpressionTypeEnum.CONTAINS)
                .limit(2)
                .offset(0);

        // ------------------------------------
        SearchPkgsResult result = pkgApiService.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.getTotal()).isEqualTo(1);
        Assertions.assertThat(result.getItems().size()).isEqualTo(1);
        Assertions.assertThat(result.getItems().get(0).getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getItems().get(0).getVersions().get(0).getSummary()).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testGetPkg_found_specific() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequestEnvelope request = new GetPkgRequestEnvelope()
                .architectureCode("x86_64")
                .name("pkg1")
                .repositorySourceCode("testreposrc_xyz")
                .versionType(PkgVersionType.SPECIFIC)
                .naturalLanguageCode("en")
                .major("1")
                .micro("2")
                .minor(null)
                .preRelease(null)
                .revision(4);

        // ------------------------------------
        GetPkgResult result = pkgApiService.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getVersions().size()).isEqualTo(1);
        Assertions.assertThat(result.getVersions().get(0).getTitle()).isEqualTo("Package 1");
        Assertions.assertThat(result.getVersions().get(0).getArchitectureCode()).isEqualTo("x86_64");
        Assertions.assertThat(result.getVersions().get(0).getMajor()).isEqualTo("1");
        Assertions.assertThat(result.getVersions().get(0).getMicro()).isEqualTo("2");
        Assertions.assertThat(result.getVersions().get(0).getRevision()).isEqualTo(4);
        Assertions.assertThat(result.getVersions().get(0).getDescription()).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.getVersions().get(0).getSummary()).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    /**
     * <p>In this test, an German localization is requested, but there is no localization present for German so it will
     * fall back English.</p>
     */

    @Test
    public void testGetPkg_found_latest() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequestEnvelope request = new GetPkgRequestEnvelope()
                .architectureCode("x86_64")
                .name("pkg1")
                .repositorySourceCode("testreposrc_xyz")
                .versionType(PkgVersionType.LATEST)
                .naturalLanguageCode("de");

        // ------------------------------------
        GetPkgResult result = pkgApiService.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getVersions().size()).isEqualTo(1);
        Assertions.assertThat(result.getVersions().get(0).getArchitectureCode()).isEqualTo("x86_64");
        Assertions.assertThat(result.getVersions().get(0).getMajor()).isEqualTo("1");
        Assertions.assertThat(result.getVersions().get(0).getMicro()).isEqualTo("2");
        Assertions.assertThat(result.getVersions().get(0).getRevision()).isEqualTo(4);
        Assertions.assertThat(result.getVersions().get(0).getDescription()).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.getVersions().get(0).getSummary()).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testGetPkg_notFound() {
        integrationTestSupportService.createStandardTestData();

        GetPkgRequestEnvelope request = new GetPkgRequestEnvelope()
                .architectureCode("x86_64")
                .name("pkg9")
                .repositorySourceCode("testreposrc_xyz")
                .versionType(PkgVersionType.LATEST)
                .naturalLanguageCode("de");

        // ------------------------------------
        ObjectNotFoundException onfe = Assert.assertThrows(ObjectNotFoundException.class, () -> pkgApiService.getPkg(request));
        // ------------------------------------

        Assertions.assertThat(onfe.getEntityName()).isEqualTo(Pkg.class.getSimpleName());
        Assertions.assertThat(onfe.getIdentifier()).isEqualTo("pkg9");
    }

    @Test
    public void testUpdatePkg() {
        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        UpdatePkgRequestEnvelope request = new UpdatePkgRequestEnvelope()
                .name("pkg1")
                .addFilterItem(UpdatePkgFilter.IS_NATIVE_DESKTOP)
                .isNativeDesktop(true);

        // ------------------------------------
        pkgApiService.updatePkg(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkgAfter = Pkg.getByName(context, "pkg1");
            Assertions.assertThat(pkgAfter.getIsNativeDesktop()).isTrue();
        }
    }

    @Test
    public void testGetPkgIcons() {
        integrationTestSupportService.createStandardTestData();

        GetPkgIconsRequestEnvelope request = new GetPkgIconsRequestEnvelope()
                .pkgName("pkg1");

        // ------------------------------------
        GetPkgIconsResult result = pkgApiService.getPkgIcons(request);
        // ------------------------------------

        Assertions.assertThat(result.getPkgIcons().size()).isEqualTo(3);
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

        ConfigurePkgIconRequestEnvelope request = new ConfigurePkgIconRequestEnvelope()
                .pkgName("pkg1")
                .pkgIcons(List.of(
                        new ConfigurePkgIconPkgIcon()
                                .size(16)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sampleHvif)),
                        new ConfigurePkgIconPkgIcon()
                                .size(32)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sampleHvif)),
                        new ConfigurePkgIconPkgIcon()
                                .size(64)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sampleHvif))
                        ));

        // ------------------------------------
        BadPkgIconException bpie = Assert.assertThrows(BadPkgIconException.class, () -> pkgApiService.configurePkgIcon(request));
        // ------------------------------------

        Assertions.assertThat(bpie.getSize()).isEqualTo(16);
        Assertions.assertThat(bpie.getMediaTypeCode()).isEqualTo(MediaType.PNG.toString());
    }

    /**
     * <p>This test will configure the icons for the package.</p>
     */

    @Test
    public void testConfigurePkgIcon_ok_bitmap() throws Exception {

        setAuthenticatedUserToRoot();
        integrationTestSupportService.createStandardTestData();

        byte[] sample16 = getResourceData("sample-16x16-b.png");
        byte[] sample32 = getResourceData("sample-32x32-b.png");
        byte[] sample64 = getResourceData("sample-64x64-b.png");

        ConfigurePkgIconRequestEnvelope request = new ConfigurePkgIconRequestEnvelope()
                .pkgName("pkg1")
                .pkgIcons(List.of(
                        new ConfigurePkgIconPkgIcon()
                                .size(16)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sample16)),
                        new ConfigurePkgIconPkgIcon()
                                .size(32)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sample32)),
                        new ConfigurePkgIconPkgIcon()
                                .size(64)
                                .mediaTypeCode("image/png")
                                .dataBase64(Base64.getEncoder().encodeToString(sample64))
                ));

        // ------------------------------------
        pkgApiService.configurePkgIcon(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkgAfter = Pkg.getByName(context, "pkg1");
            PkgSupplement pkgSupplementAfter = pkgAfter.getPkgSupplement();

            org.haiku.haikudepotserver.dataobjects.MediaType mediaTypePng
                    = org.haiku.haikudepotserver.dataobjects.MediaType.getByCode(
                    context,
                    MediaType.PNG.toString());

            Assertions.assertThat(pkgSupplementAfter.getPkgIcons().size()).isEqualTo(3);

            PkgIcon pkgIcon16 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 16);
            Assertions.assertThat(pkgIcon16.getPkgIconImage().getData()).isEqualTo(sample16);

            PkgIcon pkgIcon32 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 32);
            Assertions.assertThat(pkgIcon32.getPkgIconImage().getData()).isEqualTo(sample32);

            PkgIcon pkgIcon64 = pkgSupplementAfter.getPkgIcon(mediaTypePng, 64);
            Assertions.assertThat(pkgIcon64.getPkgIconImage().getData()).isEqualTo(sample64);

            List<PkgSupplementModification> pkgSupplementModifications = ObjectSelect.query(PkgSupplementModification.class)
                    .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkgAfter.getPkgSupplement()))
                    .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                    .select(context);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(3);

            List<PkgSupplementModification> pkgSupplementModificationsIcons = pkgSupplementModifications.subList(
                    pkgSupplementModifications.size() - 3, pkgSupplementModifications.size());

            for (PkgSupplementModification pkgSupplementModificationsIcon : pkgSupplementModificationsIcons) {
                Assertions.assertThat(pkgSupplementModificationsIcon.getUserDescription()).isEqualTo("root");
                Assertions.assertThat(pkgSupplementModificationsIcon.getUser().getNickname()).isEqualTo("root");
                Assertions.assertThat(pkgSupplementModificationsIcon.getOriginSystemDescription()).isEqualTo("hds");
            }

            List<String> modificationsContentsIcon = pkgSupplementModificationsIcons.stream().map(PkgSupplementModification::getContent).toList();

            Assertions.assertThat(modificationsContentsIcon).contains(
                    "add icon for pkg [pkg1]; size [16]; media type [image/png]; sha256 [1efe577af19a58bf77e08e0014152c3ea84c56e3a6457b35799fa23fe0df92f4]",
                    "add icon for pkg [pkg1]; size [32]; media type [image/png]; sha256 [3119b1789f6a76b518246a6cdb56d25b3b32fa7d7fb3e21adad675ebb924af35]",
                    "add icon for pkg [pkg1]; size [64]; media type [image/png]; sha256 [deb2d2191c4412debbc26d8c8411b880fda35355bddfd90a4d2d29ef7c8a0563]"
            );
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

        ConfigurePkgIconRequestEnvelope request = new ConfigurePkgIconRequestEnvelope()
                .pkgName("pkg1")
                .pkgIcons(List.of(
                        new ConfigurePkgIconPkgIcon()
                                .size(null)
                                .mediaTypeCode("application/x-vnd.haiku-icon")
                                .dataBase64(Base64.getEncoder().encodeToString(sampleHvif))
                ));

        // ------------------------------------
        pkgApiService.configurePkgIcon(request);
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

        RemovePkgIconRequestEnvelope request = new RemovePkgIconRequestEnvelope()
                .pkgName("pkg1");

        // ------------------------------------
        pkgApiService.removePkgIcon(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            PkgSupplement pkgSupplementAfter = Pkg.getByName(context, "pkg1").getPkgSupplement();
            Assertions.assertThat(pkgSupplementAfter.getPkgIcons().size()).isEqualTo(0);

            List<PkgSupplementModification> pkgSupplementModifications = ObjectSelect.query(PkgSupplementModification.class)
                    .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkgSupplementAfter))
                    .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                    .select(context);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(1);

            PkgSupplementModification pkgSupplementModificationLast = pkgSupplementModifications.getLast();
            Assertions.assertThat(pkgSupplementModificationLast.getUserDescription()).isEqualTo("root");
            Assertions.assertThat(pkgSupplementModificationLast.getUser().getNickname()).isEqualTo("root");
            Assertions.assertThat(pkgSupplementModificationLast.getOriginSystemDescription()).isEqualTo("hds");
            Assertions.assertThat(pkgSupplementModificationLast.getContent()).isEqualTo("remove icon for pkg [pkg1]");
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshots() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetPkgScreenshotsRequestEnvelope request = new GetPkgScreenshotsRequestEnvelope()
                .pkgName("pkg1");

        // ------------------------------------
        GetPkgScreenshotsResult result = pkgApiService.getPkgScreenshots(request);
        // ------------------------------------

        PkgSupplement pkgSupplement = data.pkg1.getPkgSupplement();
        Assertions.assertThat(result.getItems().size()).isEqualTo(pkgSupplement.getPkgScreenshots().size());
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshots = pkgSupplement.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshots).hasSize(3);

        int[] widths = { 320, 240, 320 };
        int[] heights = { 240, 320, 240 };
        int[] lengths = { 41296, 28303, 33201 };

        for(int i = 0; i < sortedScreenshots.size(); i++) {
            org.haiku.haikudepotserver.dataobjects.PkgScreenshot pkgScreenshot = sortedScreenshots.get(i);
            GetPkgScreenshotsScreenshot apiPkgScreenshot = result.getItems().get(i);

            Assertions.assertThat(pkgScreenshot.getCode()).isEqualTo(apiPkgScreenshot.getCode());
            Assertions.assertThat(pkgScreenshot.getWidth()).isEqualTo(apiPkgScreenshot.getWidth());
            Assertions.assertThat(pkgScreenshot.getHeight()).isEqualTo(apiPkgScreenshot.getHeight());
            Assertions.assertThat(pkgScreenshot.getLength()).isEqualTo(apiPkgScreenshot.getLength());

            Assertions.assertThat(apiPkgScreenshot.getWidth()).isEqualTo(widths[i]);
            Assertions.assertThat(apiPkgScreenshot.getHeight()).isEqualTo(heights[i]);
            Assertions.assertThat(apiPkgScreenshot.getLength()).isEqualTo(lengths[i]);
        }
    }

    /**
     * <p>This test depends on the sample package pkg1 having some screenshots associated with it.</p>
     */

    @Test
    public void testGetPkgScreenshot() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        String code = data.pkg1.getPkgSupplement().getSortedPkgScreenshots().get(0).getCode();

        GetPkgScreenshotRequestEnvelope request = new GetPkgScreenshotRequestEnvelope()
                .code(code);

        // ------------------------------------
        GetPkgScreenshotResult result = pkgApiService.getPkgScreenshot(request);
        // ------------------------------------

        Assertions.assertThat(result.getCode()).isEqualTo(code);
        Assertions.assertThat(result.getWidth()).isEqualTo(320);
        Assertions.assertThat(result.getHeight()).isEqualTo(240);
        Assertions.assertThat(result.getLength()).isEqualTo(41296);
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
        final String hash1 = sortedScreenshotsBefore.get(1).getHashSha256();

        RemovePkgScreenshotRequestEnvelope request = new RemovePkgScreenshotRequestEnvelope()
                .code(code1);

        // ------------------------------------
        pkgApiService.removePkgScreenshot(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = Pkg.getByName(context, data.pkg1.getName());
        PkgSupplement pkgSupplementAfter = pkg.getPkgSupplement();
        List<org.haiku.haikudepotserver.dataobjects.PkgScreenshot> sortedScreenshotsAfter
                = pkgSupplementAfter.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(sortedScreenshotsBefore.size()-1);
        Assertions.assertThat(sortedScreenshotsAfter.stream().anyMatch(s -> s.getCode().equals(code1))).isFalse();

        List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg);
        Assertions.assertThat(modifications.size()).isGreaterThanOrEqualTo(1);

        PkgSupplementModification modification = modifications.getLast();
        Assertions.assertThat(modification.getUser().getNickname()).isEqualTo("root");
        Assertions.assertThat(modification.getUserDescription()).isEqualTo("root");
        Assertions.assertThat(modification.getOriginSystemDescription()).isEqualTo("hds");
        Assertions.assertThat(modification.getContent()).isEqualTo(
                String.format("did delete screenshot [%s]; sha256 [%s]", code1, hash1));
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

        ReorderPkgScreenshotsRequestEnvelope request = new ReorderPkgScreenshotsRequestEnvelope()
                .pkgName("pkg1")
                .codes(List.of(
                        sortedScreenshotsBefore.get(2).getCode(),
                        sortedScreenshotsBefore.get(0).getCode()
                ));

        // ------------------------------------
        pkgApiService.reorderPkgScreenshots(request);
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

        UpdatePkgLocalizationRequestEnvelope request = new UpdatePkgLocalizationRequestEnvelope()
                .pkgName("pkg1")
                .pkgLocalizations(List.of(
                        new UpdatePkgLocalizationLocalization()
                                .naturalLanguageCode("en")
                                .title("flourescence"),
                        new UpdatePkgLocalizationLocalization()
                                .naturalLanguageCode("fr")
                                .title("treacle")
                ));

        // ------------------------------------
        pkgApiService.updatePkgLocalization(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            List<String[]> rules = ImmutableList.of(
                    new String[] { NaturalLanguageCoordinates.LANGUAGE_CODE_ENGLISH, "flourescence" },
                    new String[] { NaturalLanguageCoordinates.LANGUAGE_CODE_FRENCH, "treacle" },
                    new String[] { NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN, "Packet 1" }
            );

            for(String[] rule : rules) {
                NaturalLanguageCoded naturalLanguage = NaturalLanguageCoordinates.fromCode(rule[0]);
                Assertions.assertThat(
                        org.haiku.haikudepotserver.dataobjects.PkgLocalization.getForPkgAndNaturalLanguage(context, pkg1, naturalLanguage).getTitle()
                ).isEqualTo(rule[1]);
            }
        }

        {
            ObjectContext context = serverRuntime.newContext();
            PkgSupplement pkg1Supplement = Pkg.getByName(context, "pkg1").getPkgSupplement();

            List<PkgSupplementModification> pkgSupplementModifications = ObjectSelect.query(PkgSupplementModification.class)
                    .where(PkgSupplementModification.PKG_SUPPLEMENT.eq(pkg1Supplement))
                    .orderBy(PkgSupplementModification.CREATE_TIMESTAMP.getName(), SortOrder.ASCENDING)
                    .select(context);

            List<PkgSupplementModification> relevantPkgSupplementModifications = pkgSupplementModifications.subList(
                    pkgSupplementModifications.size() - 2, pkgSupplementModifications.size());

            for (PkgSupplementModification pkgSupplementModification : relevantPkgSupplementModifications) {
                Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("root");
                Assertions.assertThat(pkgSupplementModification.getUser().getNickname()).isEqualTo("root");
                Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("hds");
            }

            List<String> relevantPkgSupplementModificationContents = relevantPkgSupplementModifications.stream()
                    .map(PkgSupplementModification::getContent)
                    .toList();
            Assertions.assertThat(relevantPkgSupplementModificationContents).contains(
                    """
changing localization for pkg [pkg1] in natural language [en];
title: [flourescence]""",
                    """
changing localization for pkg [pkg1] in natural language [fr];
title: [treacle]"""
            );
        }

    }

    /**
     * <p>This test checks that it is possible to get a couple of known localizations for a known pkg.</p>
     */

    @Test
    public void testGetPkgLocalizations() {
        integrationTestSupportService.createStandardTestData();

        GetPkgLocalizationsRequestEnvelope request = new GetPkgLocalizationsRequestEnvelope()
                .naturalLanguageCodes(List.of("en", "de"))
                .pkgName("pkg1");

        // ------------------------------------
        GetPkgLocalizationsResult result = pkgApiService.getPkgLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.getPkgLocalizations().size()).isEqualTo(2);

        Map<String, String> languageToTitles = result.getPkgLocalizations()
                .stream()
                .collect(Collectors.toMap(
                        GetPkgLocalizationsPkgLocalization::getNaturalLanguageCode,
                        GetPkgLocalizationsPkgLocalization::getTitle));

        Assertions.assertThat(languageToTitles.get("en")).isEqualTo("Package 1");
        Assertions.assertThat(languageToTitles.get("de")).isEqualTo("Packet 1");
    }

    /**
     * <p>This test requests german and english, but only english is present so needs to check that the output
     * contains only the english data.</p>
     */

    @Test
    public void testGetPkgVersionLocalizations() {
        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        GetPkgVersionLocalizationsRequestEnvelope request = new GetPkgVersionLocalizationsRequestEnvelope()
                .architectureCode("x86_64")
                .repositorySourceCode("testreposrc_xyz")
                .naturalLanguageCodes(List.of("en", "de"))
                .pkgName("pkg1");

        // ------------------------------------
        GetPkgVersionLocalizationsResult result = pkgApiService.getPkgVersionLocalizations(request);
        // ------------------------------------

        Assertions.assertThat(result.getPkgVersionLocalizations().size()).isEqualTo(1);
        Assertions.assertThat(result.getPkgVersionLocalizations().get(0).getDescription()).isEqualTo("pkg1Version2DescriptionEnglish_rockmelon");
        Assertions.assertThat(result.getPkgVersionLocalizations().get(0).getSummary()).isEqualTo("pkg1Version2SummaryEnglish_persimon");
    }

    @Test
    public void testUpdatePkgProminence() {

        setAuthenticatedUserToRoot();

        integrationTestSupportService.createStandardTestData();

        UpdatePkgProminenceRequestEnvelope request = new UpdatePkgProminenceRequestEnvelope()
                .pkgName("pkg1")
                .prominenceOrdering(200)
                .repositoryCode("testrepo");

        // ------------------------------------
        pkgApiService.updatePkgProminence(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            Repository repository = Repository.getByCode(context, "testrepo");
            Assertions.assertThat(pkg1.getPkgProminence(repository).getProminence().getOrdering()).isEqualTo(200);
        }

    }

    @Test
    public void testGetPkgChangelog() {
        integrationTestSupportService.createStandardTestData();

        GetPkgChangelogRequestEnvelope request = new GetPkgChangelogRequestEnvelope()
                .pkgName("pkg1");

        // ------------------------------------
        GetPkgChangelogResult result = pkgApiService.getPkgChangelog(request);
        // ------------------------------------

        Assertions.assertThat(result.getContent()).isEqualTo("Stadt\nKarlsruhe");
    }

    /**
     * <p>This will override the change log that was there with the new value.</p>
     */

    @Test
    public void testUpdatePkgChangelog_withContent() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdatePkgChangelogRequestEnvelope request = new UpdatePkgChangelogRequestEnvelope()
                .pkgName("pkg1")
                .content("  das Zimmer  ");

        // not ideal, but ensure total ordering on the pkg supplement modifications results
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100L));

        // ------------------------------------
        pkgApiService.updatePkgChangelog(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkgAfter = Pkg.getByName(context, "pkg1");
            Assertions.assertThat(pkgAfter.getPkgSupplement().getPkgChangelog().get().getContent()).isEqualTo("das Zimmer");

            List<PkgSupplementModification> pkgSupplementModifications = PkgSupplementModification.findForPkg(context, pkgAfter);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(1);

            PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.getLast();

            Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("root");
            Assertions.assertThat(pkgSupplementModification.getUser().getNickname()).isEqualTo("root");
            Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("hds");
            Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo(
                    """
updated the changelog for [pkg1];
das Zimmer"""
            );
        }

    }

    /**
     * <p>Writing in no content will mean that the change log that was there is now removed.</p>
     */

    @Test
    public void testUpdatePkgChangelog_withNoContent() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdatePkgChangelogRequestEnvelope request = new UpdatePkgChangelogRequestEnvelope()
                .pkgName("pkg1")
                .content("");

        // ------------------------------------
        pkgApiService.updatePkgChangelog(request);
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

        UpdatePkgVersionRequestEnvelope request = new UpdatePkgVersionRequestEnvelope()
                .pkgName("pkg1")
                .repositorySourceCode("testreposrc_xyz")
                .architectureCode("x86_64")
                .major("1")
                .micro("2")
                .revision(3)
                .active(false)
                .filter(List.of(UpdatePkgVersionFilter.ACTIVE));

        // ------------------------------------
        pkgApiService.updatePkgVersion(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            Architecture architecture = Architecture.getByCode(context, "x86_64");
            PkgVersion pkgVersion = PkgVersion.getForPkg(context, pkg1, repositorySource, architecture, new VersionCoordinates("1",null,"2",null,3));
            Assertions.assertThat(pkgVersion.getActive()).isFalse();
        }
    }

    @Test
    public void testIncrementViewCounter() {
        integrationTestSupportService.createStandardTestData();

        IncrementViewCounterRequestEnvelope request = new IncrementViewCounterRequestEnvelope()
                .major("1")
                .micro("2")
                .revision(3)
                .name("pkg1")
                .architectureCode("x86_64")
                .repositoryCode("testrepo");

        // ------------------------------------
        pkgApiService.incrementViewCounter(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            Architecture architecture = Architecture.getByCode(context, "x86_64");
            PkgVersion pkgVersion = PkgVersion.getForPkg(context, pkg1, repositorySource, architecture, new VersionCoordinates("1",null,"2",null,3));
            Assertions.assertThat(pkgVersion.getViewCounter()).isEqualTo(1L);
        }

    }

}
