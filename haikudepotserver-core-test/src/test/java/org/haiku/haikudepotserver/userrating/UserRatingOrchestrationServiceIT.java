/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating;

import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@ContextConfiguration(classes = TestConfig.class)
public class UserRatingOrchestrationServiceIT extends AbstractIntegrationTest {

    /**
     * <p>Note that this test will actually check the implementation and not the interface to the implementation.</p>
     */

    @Resource
    private UserRatingServiceImpl userRatingServiceImpl;

    @Resource
    private PkgService pkgService;

    // -------------------
    // SETUP

    private UserRating createUserRating(
        ObjectContext context,
        PkgVersion pkgVersion,
        User user,
        Short rating) {
        UserRating userRating = context.newObject(UserRating.class);
        userRating.setUser(user);
        userRating.setPkgVersion(pkgVersion);
        userRating.setNaturalLanguage(NaturalLanguage.tryGetByCode(context, NaturalLanguage.CODE_ENGLISH).get());
        userRating.setRating(rating);
        return userRating;
    }

    private PkgVersion createTestUserRatingPkgVersion(
            ObjectContext context,
            RepositorySource repositorySource, Pkg pkg, Architecture architecture,
            Integer major, Integer minor, Integer micro, Integer revision,
            boolean isLatest) {
        PkgVersion pkgVersion = context.newObject(PkgVersion.class);
        pkgVersion.setIsLatest(isLatest);
        pkgVersion.setRepositorySource(repositorySource);
        pkgVersion.setRevision(revision);
        pkgVersion.setArchitecture(architecture);
        pkgVersion.setMajor(Integer.toString(major));
        pkgVersion.setMinor(Integer.toString(minor));
        pkgVersion.setMicro(Integer.toString(micro));
        pkgVersion.setPkg(pkg);
        return pkgVersion;
    }

    private UserRatingTestData createTestUserRatingData(ObjectContext context) {

        UserRatingTestData userRatingTestData = new UserRatingTestData();

        Repository repository = Repository.tryGetByCode(context, "testrepo").get();
        RepositorySource repositorySource = RepositorySource.tryGetByCode(context, "testreposrc_xyz").get();
        Architecture x86_64 = Architecture.tryGetByCode(context, "x86_64").get();
        Architecture x86_gcc2 = Architecture.tryGetByCode(context, "x86_gcc2").get();

        userRatingTestData.pkg = integrationTestSupportService.createPkg(context, "urtestpkg");

        pkgService.ensurePkgProminence(context, userRatingTestData.pkg, repository, Prominence.ORDERING_LAST);

        userRatingTestData.user1 = integrationTestSupportService.createBasicUser(context,"urtestuser1","password");
        userRatingTestData.user2 = integrationTestSupportService.createBasicUser(context,"urtestuser2","password");
        userRatingTestData.user3 = integrationTestSupportService.createBasicUser(context,"urtestuser3","password");
        userRatingTestData.user4 = integrationTestSupportService.createBasicUser(context,"urtestuser4","password");
        userRatingTestData.user5 = integrationTestSupportService.createBasicUser(context,"urtestuser5","password");

        userRatingTestData.pkgVersion_0_0_9__x86_gcc2 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_64, 0, 0, 9, null, false);
        userRatingTestData.pkgVersion_1_0_0__x86_gcc2 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_64, 1, 0, 0, null, false);
        userRatingTestData.pkgVersion_1_0_1__x86_gcc2 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_64, 1, 0, 1, null, false);
        userRatingTestData.pkgVersion_1_0_1_1__x86_gcc2 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_64, 1, 0, 1, 1, false);
        userRatingTestData.pkgVersion_1_0_2__x86_gcc2 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_64, 1, 0, 2, null, false);
        userRatingTestData.pkgVersion_1_0_2__x86_64 = createTestUserRatingPkgVersion(context, repositorySource, userRatingTestData.pkg, x86_gcc2, 1, 0, 2, null, false);

        return userRatingTestData;
    }

    // -------------------
    // TESTS

    @Test
    public void testUserRatingDerivation_mixed() {

        integrationTestSupportService.createStandardTestData();
        ObjectContext context = serverRuntime.newContext();
        UserRatingTestData userRatingData = createTestUserRatingData(context);
        context.commitChanges();

        createUserRating(context, userRatingData.pkgVersion_0_0_9__x86_gcc2, userRatingData.user5, (short) 2);
        createUserRating(context, userRatingData.pkgVersion_1_0_1_1__x86_gcc2, userRatingData.user1, (short) 3);
        createUserRating(context, userRatingData.pkgVersion_1_0_0__x86_gcc2, userRatingData.user3, (short) 2);
        createUserRating(context, userRatingData.pkgVersion_1_0_2__x86_gcc2, userRatingData.user2, (short) 3);
        createUserRating(context, userRatingData.pkgVersion_1_0_1__x86_gcc2, userRatingData.user4, (short) 1);
        context.commitChanges();

        // we want to get a time-delay on the latter user rating in order that we take the latter one
        // in the derivation.

        createUserRating(context, userRatingData.pkgVersion_1_0_2__x86_gcc2, userRatingData.user1, (short) 4);
        context.commitChanges();
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        createUserRating(context, userRatingData.pkgVersion_1_0_2__x86_64, userRatingData.user1, (short) 1);
        context.commitChanges();

        // ----------------------------
        Optional<UserRatingServiceImpl.DerivedUserRating> result = userRatingServiceImpl.userRatingDerivation(
                context,
                userRatingData.pkg,
                Repository.tryGetByCode(context, "testrepo").get());
        // ----------------------------

        Assertions.assertThat(result.isPresent()).isTrue();
        Assertions.assertThat(result.get().getRating()).isEqualTo(1.75f);
        Assertions.assertThat(result.get().getSampleSize()).isEqualTo(4);

    }

    public static class UserRatingTestData {

        public Pkg pkg;

        public User user1;
        public User user2;
        public User user3;
        public User user4;
        public User user5;

        public PkgVersion pkgVersion_0_0_9__x86_gcc2;
        public PkgVersion pkgVersion_1_0_0__x86_gcc2;
        public PkgVersion pkgVersion_1_0_1__x86_gcc2;
        public PkgVersion pkgVersion_1_0_1_1__x86_gcc2;
        public PkgVersion pkgVersion_1_0_2__x86_gcc2;
        public PkgVersion pkgVersion_1_0_2__x86_64;

    }


}
