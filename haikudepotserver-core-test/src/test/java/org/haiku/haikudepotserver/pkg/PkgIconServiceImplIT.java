/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.NonUserPkgSupplementModificationAgent;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.UserPkgSupplementModificationAgent;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

@ContextConfiguration(classes = TestConfig.class)
public class PkgIconServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private PkgIconService pkgIconService;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Test
    public void testRemovePkgIcon() {

        integrationTestSupportService.createStandardTestData();

        // not ideal, but ensure total ordering on the pkg supplement modifications results
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100L));

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");

            // ---------------------------------
            pkgIconService.removePkgIcon(
                    context,
                    new NonUserPkgSupplementModificationAgent("sam", "some system"),
                    pkg1.getPkgSupplement());
            // ---------------------------------

            context.commitChanges();
        }

        // now verify that the icon was removed and that the modification record was written.

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 = org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            Assertions.assertThat(pkg1.getPkgSupplement().getPkgIcons()).isEmpty();

            List<PkgSupplementModification> pkgSupplementModifications = PkgSupplementModification.findForPkg(context, pkg1);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(1);
            // ^ there are four because there were three added to start with and then one more from this test.

            PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.getLast();
            Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("sam");
            Assertions.assertThat(pkgSupplementModification.getUser()).isNull();
            Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("some system");
            Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo("remove icon for pkg [pkg1]");
        }

    }

    /**
     * <p>This method will test adding an icon to a package that has the icon already. In this case we want to
     * see that nothing happened; no change was recorded.</p>
     */

    @Test
    public void testStorePkgIconImage_noChange() throws Exception {

        integrationTestSupportService.createStandardTestData();

        int pkgSupplementModificationsPrior;

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            pkgSupplementModificationsPrior = PkgSupplementModification.findForPkg(context, pkg1).size();
        }

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());

            // not ideal, but this will ensure an ordering on the pkg supplement modifications later
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100L));

            try(InputStream inputStream = Resources.asByteSource(Resources.getResource("sample-32x32.png")).openStream()) {

                // ---------------------------------
                pkgIconService.storePkgIconImage(
                        inputStream,
                        pngMediaType,
                        32,
                        context,
                        new UserPkgSupplementModificationAgent(null),
                        pkg1.getPkgSupplement());
                // ---------------------------------

            }

            context.commitChanges();
        }

        // now verify that the icon remained the same and that no more modifications were captured.

        {
            ObjectContext context = serverRuntime.newContext();
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 = org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            PkgIcon pkgIcon = pkg1.getPkgSupplement().getPkgIcon(pngMediaType, 32);

            Assertions.assertThat(pkgIcon.getSize()).isEqualTo(32);
            Assertions.assertThat(Hashing.sha256().hashBytes(pkgIcon.getPkgIconImage().getData()).toString())
                    .isEqualTo("da4d440ca6667857d5c2fd7414160b854a93bbbb38a52324000c4275e1850b43");

            Assertions.assertThat(PkgSupplementModification.findForPkg(context, pkg1).size()).isEqualTo(pkgSupplementModificationsPrior);
            // ^ if there were one more then, it might be the addition of a different icon.
        }

    }

    /**
     * <p>This method will test adding an icon to a package that has no icon. It will check to see that the
     * record of what has changed was also written.</p>
     */

    @Test
    public void testStorePkgIconImage() throws Exception {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "pumpernickel", "f799a104-64c9-4bcb-9b31-a2c85630ef45");
        }

        // not ideal, but ensure total ordering on the pkg supplement modifications results
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100L));

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg2 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg2");
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());

            try(InputStream inputStream = Resources.asByteSource(Resources.getResource("sample-32x32-b.png")).openStream()) {

                // ---------------------------------
                pkgIconService.storePkgIconImage(
                        inputStream,
                        pngMediaType,
                        32,
                        context,
                        new UserPkgSupplementModificationAgent(User.getByNickname(context, "pumpernickel")),
                        pkg2.getPkgSupplement());
                // ---------------------------------

            }

            context.commitChanges();
        }

        // now verify that the icon was written and that the pkg supplement was stored.

        {
            ObjectContext context = serverRuntime.newContext();
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());
            org.haiku.haikudepotserver.dataobjects.Pkg pkg2 = org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg2");
            PkgIcon pkgIcon = pkg2.getPkgSupplement().getPkgIcon(pngMediaType, 32);

            Assertions.assertThat(pkgIcon.getSize()).isEqualTo(32);

            List<PkgSupplementModification> pkgSupplementModifications = PkgSupplementModification.findForPkg(context, pkg2);
            Assertions.assertThat(pkgSupplementModifications).hasSize(1);
            // ^ there are four because there were three added to start with and then one more from this test.

            PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.get(0);
            Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("pumpernickel");
            Assertions.assertThat(pkgSupplementModification.getUser().getNickname()).isEqualTo("pumpernickel");
            Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("hds");
            Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo(
                    "add icon for pkg [pkg2]; size [32]; media type [image/png]; sha256 [3119b1789f6a76b518246a6cdb56d25b3b32fa7d7fb3e21adad675ebb924af35]"
            );
        }

    }

    /**
     * <p>When a "_devel" package exists and an update is made to the icon of the parent
     * package then the icon should be visible from the "_devel" package too. The `pkg1` and
     * the `pkg1_devel` have the same pkg supplement.</p>
     *
     * <p>In this test, a package is already having 3 icons (16,32,64) and another one is
     * added on. Query the `pkg_devel` corollary for it's icons and we should see the same
     * icons as was added to the main `pkg`.</p>
     */

    @Test
    public void testStorePkgIconImage_develPkgHandling() throws Exception {

        // setup the two packages.

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "pumpernickel", "f799a104-64c9-4bcb-9b31-a2c85630ef45");
        }

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1Devel = context.newObject(org.haiku.haikudepotserver.dataobjects.Pkg.class);
            pkg1Devel.setActive(true);
            pkg1Devel.setIsNativeDesktop(false);
            pkg1Devel.setName("pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT);
            pkg1Devel.setPkgSupplement(PkgSupplement.getByBasePkgName(context, "pkg1"));
            context.commitChanges();
        }

        // not ideal, but ensure total ordering on the pkg supplement modifications results
        Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100L));

        {
            ObjectContext context = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1 =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(context, "pkg1");
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());

            try(InputStream inputStream = Resources.asByteSource(Resources.getResource("sample-32x32-b.png")).openStream()) {

                // ---------------------------------
                pkgIconService.storePkgIconImage(
                        inputStream,
                        pngMediaType,
                        32,
                        context,
                        new UserPkgSupplementModificationAgent(User.getByNickname(context, "pumpernickel")),
                        pkg1.getPkgSupplement());
                // ---------------------------------

            }

            context.commitChanges();
        }

        {
            ObjectContext context = serverRuntime.newContext();
            MediaType pngMediaType = MediaType.getByCode(context, com.google.common.net.MediaType.PNG.toString());
            org.haiku.haikudepotserver.dataobjects.Pkg pkg1Devel =
                    org.haiku.haikudepotserver.dataobjects.Pkg.getByName(
                            context,
                            "pkg1" + PkgServiceImpl.SUFFIX_PKG_DEVELOPMENT);

            PkgIcon pkgIcon = pkg1Devel.getPkgSupplement().getPkgIcon(pngMediaType, 32);

            Assertions.assertThat(pkgIcon.getSize()).isEqualTo(32);

            // As the image was changed there should be 3 entries; one for each of the 16, 32 and 64 pixel images.

            List<PkgSupplementModification> pkgSupplementModifications = PkgSupplementModification.findForPkg(context, pkg1Devel);
            Assertions.assertThat(pkgSupplementModifications.size()).isGreaterThanOrEqualTo(1);
            // ^ there are four because there were three added to start with and then one more from this test.

            PkgSupplementModification pkgSupplementModification = pkgSupplementModifications.getLast();
            Assertions.assertThat(pkgSupplementModification.getUserDescription()).isEqualTo("pumpernickel");
            Assertions.assertThat(pkgSupplementModification.getUser().getNickname()).isEqualTo("pumpernickel");
            Assertions.assertThat(pkgSupplementModification.getOriginSystemDescription()).isEqualTo("hds");
            Assertions.assertThat(pkgSupplementModification.getContent()).isEqualTo(
                    "add icon for pkg [pkg1]; size [32]; media type [image/png]; sha256 [3119b1789f6a76b518246a6cdb56d25b3b32fa7d7fb3e21adad675ebb924af35]"
            );
        }
    }


}
