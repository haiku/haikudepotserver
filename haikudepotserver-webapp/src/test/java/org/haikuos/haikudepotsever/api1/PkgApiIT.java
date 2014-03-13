/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.MediaType;
import com.googlecode.jsonrpc4j.Base64;
import junit.framework.Assert;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.PkgApi;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.api1.support.IntegrationTestSupportService;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.List;

public class PkgApiIT extends AbstractIntegrationTest {

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    @Resource
    PkgApi pkgApi;

    @Resource
    PkgService pkgService;

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
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "GAMES").get());
                pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            }

            {
                PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
                pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "BUSINESS").get());
                pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            }

            context.commitChanges();
        }

        UpdatePkgCategoriesRequest request = new UpdatePkgCategoriesRequest();
        request.pkgName = data.pkg1.getName();
        request.pkgCategoryCodes = ImmutableList.of("BUSINESS", "DEVELOPMENT");

        // ------------------------------------
        pkgApi.updatePkgCategories(request);
        // ------------------------------------

        // now we need to check on those categories.  GAMES should have gone, BUSINESS should remain
        // and DEVELOPMENT should be added.

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg = Pkg.getByName(context, data.pkg1.getName()).get();

            Assertions.assertThat(ImmutableSet.of("BUSINESS", "DEVELOPMENT")).isEqualTo(
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
    public void searchPkgsTest() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        SearchPkgsRequest request = new SearchPkgsRequest();
        request.architectureCode = "x86";
        request.expression = "pk";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchPkgsResult result = pkgApi.searchPkgs(request);
        // ------------------------------------

        Assertions.assertThat(result.hasMore).isTrue();
        Assertions.assertThat(result.items.size()).isEqualTo(2);
        Assertions.assertThat(result.items.get(0).name).isEqualTo("pkg1");
        Assertions.assertThat(result.items.get(1).name).isEqualTo("pkg2");
    }

    @Test
    public void testGetPkg_found() throws ObjectNotFoundException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86";
        request.name = "pkg1";
        request.versionType = GetPkgRequest.VersionType.LATEST;

        // ------------------------------------
        GetPkgResult result = pkgApi.getPkg(request);
        // ------------------------------------

        Assertions.assertThat(result.name).isEqualTo("pkg1");
        Assertions.assertThat(result.versions.size()).isEqualTo(1);
        Assertions.assertThat(result.versions.get(0).architectureCode).isEqualTo("x86");
        Assertions.assertThat(result.versions.get(0).major).isEqualTo("1");
        Assertions.assertThat(result.versions.get(0).micro).isEqualTo("2");
        Assertions.assertThat(result.versions.get(0).revision).isEqualTo(4);
    }

    @Test
    public void testGetPkg_notFound() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetPkgRequest request = new GetPkgRequest();
        request.architectureCode = "x86";
        request.name = "pkg9";
        request.versionType = GetPkgRequest.VersionType.LATEST;

        try {

            // ------------------------------------
            GetPkgResult result = pkgApi.getPkg(request);
            // ------------------------------------

            Assert.fail("expected an instance of "+ObjectNotFoundException.class.getSimpleName()+" to be thrown, but was not");
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

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        GetPkgIconsResult result = pkgApi.getPkgIcons(new GetPkgIconsRequest("pkg1"));
        // ------------------------------------

        Assertions.assertThat(result.pkgIcons.size()).isEqualTo(2);
        // check more stuff...

    }

    /**
     * <p>Here we are trying to load the HVIF data in as PNG images.</p>
     */

    @Test
    public void testConfigurePkgIcon_badData() throws Exception {

        setAuthenticatedUserToRoot();
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        byte[] sample16 = getResourceData("/sample-16x16.png");
        byte[] sample32 = getResourceData("/sample-32x32.png");
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
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
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

            Optional<PkgIcon> pkgIcon16Optional = pkgOptionalafter.get().getPkgIcon(mediaTypePng, 16);
            Assertions.assertThat(pkgIcon16Optional.get().getPkgIconImage().get().getData()).isEqualTo(sample16);

            Optional<PkgIcon> pkgIcon32Optional = pkgOptionalafter.get().getPkgIcon(mediaTypePng, 32);
            Assertions.assertThat(pkgIcon32Optional.get().getPkgIconImage().get().getData()).isEqualTo(sample32);

            Optional<PkgIcon> pkgIconHvifOptional = pkgOptionalafter.get().getPkgIcon(mediaTypeHvif, null);
            Assertions.assertThat(pkgIconHvifOptional.get().getPkgIconImage().get().getData()).isEqualTo(sampleHvif);
        }
    }

    /**
     * <p>This test knows that an icon exists for pkg1 and then removes it.</p>
     */

    @Test
    public void testRemoveIcon() throws Exception {

        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        {
            ObjectContext objectContext = serverRuntime.getContext();
            Optional<Pkg> pkgOptionalBefore = Pkg.getByName(objectContext, "pkg1");
            Assertions.assertThat(pkgOptionalBefore.get().getPkgIcons().size()).isEqualTo(2); // 16 and 32 px sizes
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
            GetPkgScreenshotsResult.PkgScreenshot apiPkgScreenshot = result.items.get(i);
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
        List<PkgScreenshot> sortedScreenshotsAfter = data.pkg1.getSortedPkgScreenshots();

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
        List<PkgScreenshot> sortedScreenshotsAfter = data.pkg1.getSortedPkgScreenshots();

        Assertions.assertThat(sortedScreenshotsAfter.size()).isEqualTo(3);
        Assertions.assertThat(sortedScreenshotsAfter.get(0).getCode()).isEqualTo(sortedScreenshotsBefore.get(2).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(1).getCode()).isEqualTo(sortedScreenshotsBefore.get(0).getCode());
        Assertions.assertThat(sortedScreenshotsAfter.get(2).getCode()).isEqualTo(sortedScreenshotsBefore.get(1).getCode());
    }

}