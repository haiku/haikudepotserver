/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import junit.framework.Assert;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.PkgApi;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgScreenshot;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.api1.support.IntegrationTestSupportService;
import org.junit.Test;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.List;

public class PkgApiIT extends AbstractIntegrationTest {

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    @Resource
    PkgApi pkgApi;

    @Resource
    PkgService pkgService;

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

        Assertions.assertThat(result.hasIcon).isFalse();
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

    /**
     * <p>This test will first of all load an icon in for a package and will then use the API to remove it; checking
     * that the icon is there before and is not there afterwards.</p>
     */

    @Test
    public void testRemoveIcon() throws Exception {

        setAuthenticatedUserToRoot();

        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        InputStream sample32InputStream = null;

        try {
            sample32InputStream = getClass().getResourceAsStream("/sample-32x32.png");

            if(null==sample32InputStream) {
                throw new IllegalStateException("unable to find the test input stream for the icon image");
            }

            pkgService.storePkgIconImage(
                    sample32InputStream,
                    32,
                    integrationTestSupportService.getObjectContext(),
                    data.pkg1);

            integrationTestSupportService.getObjectContext().commitChanges();
        }
        finally {
            Closeables.closeQuietly(sample32InputStream);
        }

        {
            ObjectContext objectContext = serverRuntime.getContext();
            Optional<Pkg> pkgOptionalBefore = Pkg.getByName(objectContext, "pkg1");
            Assertions.assertThat(pkgOptionalBefore.get().getPkgIcons().size()).isEqualTo(1);
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
        }
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