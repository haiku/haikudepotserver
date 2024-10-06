/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class UserRatingApiServiceIT extends AbstractIntegrationTest {

    @Resource
    UserRatingApiService userRatingApiService;

    @Resource
    PkgService pkgService;

    private String createTestUserAndSampleUserRating() {
        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "password");
        integrationTestSupportService.agreeToUserUsageConditions(context, user);

        UserRating userRating = context.newObject(UserRating.class);
        userRating.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguageCoordinates.LANGUAGE_CODE_SPANISH));
        userRating.setComment("How now brown cow");
        userRating.setPkgVersion(pkgService.getLatestPkgVersionForPkg(
                context,
                Pkg.getByName(context, "pkg1"),
                RepositorySource.getByCode(context, "testreposrc_xyz"),
                Collections.singletonList(Architecture.getByCode(context, "x86_64"))).get());
        userRating.setRating((short) 3);
        userRating.setUserRatingStability(UserRatingStability.tryGetByCode(context, UserRatingStability.CODE_VERYUNSTABLE).get());
        userRating.setUser(user);
        context.commitChanges();

        return userRating.getCode();
    }

    @Test
    public void testUpdateUserRating() {

        integrationTestSupportService.createStandardTestData();
        String userRatingCode = createTestUserAndSampleUserRating();

        setAuthenticatedUser("testuser");

        UpdateUserRatingRequestEnvelope request = new UpdateUserRatingRequestEnvelope()
                .active(false)
                .rating(1)
                .comment("Highlighter orange")
                .naturalLanguageCode("de")
                .code(userRatingCode)
                .userRatingStabilityCode("mostlystable")
                .filter(List.of(UpdateUserRatingFilter.values()));

        // ------------------------------------
        userRatingApiService.updateUserRating(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            UserRating userRating = UserRating.getByCode(context, userRatingCode);
            Assertions.assertThat(userRating.getActive()).isFalse();
            Assertions.assertThat(userRating.getRating()).isEqualTo((short) 1);
            Assertions.assertThat(userRating.getComment()).isEqualTo("Highlighter orange");
            Assertions.assertThat(userRating.getNaturalLanguage().getCode()).isEqualTo(NaturalLanguageCoordinates.LANGUAGE_CODE_GERMAN);
            Assertions.assertThat(userRating.getUserRatingStability().getCode()).isEqualTo(UserRatingStability.CODE_MOSTLYSTABLE);
        }
    }

    @Test
    public void testGetUserRating() {
        integrationTestSupportService.createStandardTestData();
        String userRatingCode = createTestUserAndSampleUserRating();

        GetUserRatingRequestEnvelope request = new GetUserRatingRequestEnvelope()
                .code(userRatingCode);

        // ------------------------------------
        GetUserRatingResult result = userRatingApiService.getUserRating(request);
        // ------------------------------------

        Assertions.assertThat(result.getActive()).isTrue();
        Assertions.assertThat(Strings.isNullOrEmpty(result.getCode())).isFalse();
        Assertions.assertThat(result.getComment()).isEqualTo("How now brown cow");
        Assertions.assertThat(result.getNaturalLanguageCode()).isEqualTo("es");
        Assertions.assertThat(result.getCreateTimestamp()).isNotNull();
        Assertions.assertThat(result.getModifyTimestamp()).isNotNull();
        Assertions.assertThat(result.getRating()).isEqualTo((short) 3);
        Assertions.assertThat(result.getUser().getNickname()).isEqualTo("testuser");
        Assertions.assertThat(result.getUserRatingStabilityCode()).isEqualTo(UserRatingStability.CODE_VERYUNSTABLE);
        Assertions.assertThat(result.getPkgVersion().getPkg().getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getPkgVersion().getArchitectureCode()).isEqualTo("x86_64");
        Assertions.assertThat(result.getPkgVersion().getMajor()).isEqualTo("1");
        Assertions.assertThat(result.getPkgVersion().getMicro()).isEqualTo("2");
        Assertions.assertThat(result.getPkgVersion().getMinor()).isNull();
        Assertions.assertThat(result.getPkgVersion().getRevision()).isEqualTo(4);
        Assertions.assertThat(result.getPkgVersion().getPreRelease()).isNull();
    }

    @Test
    public void testGetUserRatingByUserAndPkgVersion() {
        integrationTestSupportService.createStandardTestData();

        String userRatingCode = createTestUserAndSampleUserRating();

        GetUserRatingByUserAndPkgVersionRequestEnvelope request = new GetUserRatingByUserAndPkgVersionRequestEnvelope()
                .pkgName("pkg1")
                .userNickname("testuser")
                .pkgVersionArchitectureCode("x86_64")
                .pkgVersionMajor("1")
                .pkgVersionMicro("2")
                .pkgVersionMinor(null)
                .repositoryCode("testrepo")
                .pkgVersionRevision(4)
                .pkgVersionPreRelease(null);

        // ------------------------------------
        GetUserRatingByUserAndPkgVersionResult result = userRatingApiService.getUserRatingByUserAndPkgVersion(request);
        // ------------------------------------

        Assertions.assertThat(result.getCode()).isEqualTo(userRatingCode);

        Assertions.assertThat(result.getActive()).isTrue();
        Assertions.assertThat(Strings.isNullOrEmpty(result.getCode())).isFalse();
        Assertions.assertThat(result.getComment()).isEqualTo("How now brown cow");
        Assertions.assertThat(result.getNaturalLanguageCode()).isEqualTo("es");
        Assertions.assertThat(result.getCreateTimestamp()).isNotNull();
        Assertions.assertThat(result.getModifyTimestamp()).isNotNull();
        Assertions.assertThat(result.getRating()).isEqualTo((short) 3);
        Assertions.assertThat(result.getUser().getNickname()).isEqualTo("testuser");
        Assertions.assertThat(result.getUserRatingStabilityCode()).isEqualTo(UserRatingStability.CODE_VERYUNSTABLE);
        Assertions.assertThat(result.getPkgVersion().getPkg().getName()).isEqualTo("pkg1");
        Assertions.assertThat(result.getPkgVersion().getArchitectureCode()).isEqualTo("x86_64");
        Assertions.assertThat(result.getPkgVersion().getMajor()).isEqualTo("1");
        Assertions.assertThat(result.getPkgVersion().getMicro()).isEqualTo("2");
        Assertions.assertThat(result.getPkgVersion().getMinor()).isNull();
        Assertions.assertThat(result.getPkgVersion().getRevision()).isEqualTo(4);
        Assertions.assertThat(result.getPkgVersion().getPreRelease()).isNull();
    }


    @Test
    public void testCreateUserRating() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "password");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
        }

        setAuthenticatedUser("testuser");

        CreateUserRatingRequestEnvelope request = new CreateUserRatingRequestEnvelope()
                .naturalLanguageCode("es")
                .userNickname("testuser")
                .repositoryCode("testrepo")
                .userRatingStabilityCode("veryunstable")
                .comment("The supermarket has gone crazy")
                .rating(5)
                .pkgName("pkg1")
                .pkgVersionArchitectureCode("x86_64")
                .pkgVersionType(PkgVersionType.LATEST);

        // ------------------------------------
        String code = userRatingApiService.createUserRating(request).getCode();
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<UserRating> userRatingOptional = UserRating.tryGetByCode(context, code);

            Assertions.assertThat(userRatingOptional.isPresent()).isTrue();
            Assertions.assertThat(userRatingOptional.get().getActive()).isTrue();
            Assertions.assertThat(userRatingOptional.get().getComment()).isEqualTo("The supermarket has gone crazy");
            Assertions.assertThat(userRatingOptional.get().getNaturalLanguage().getCode()).isEqualTo(NaturalLanguageCoordinates.LANGUAGE_CODE_SPANISH);
            Assertions.assertThat(userRatingOptional.get().getRating()).isEqualTo((short) 5);
            Assertions.assertThat(userRatingOptional.get().getUser().getNickname()).isEqualTo("testuser");
            Assertions.assertThat(userRatingOptional.get().getUserRatingStability().getCode()).isEqualTo(UserRatingStability.CODE_VERYUNSTABLE);
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getPkg().getName()).isEqualTo("pkg1");
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getMajor()).isEqualTo("1");
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getMinor()).isNull();
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getMicro()).isEqualTo("2");
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getPreRelease()).isNull();
            Assertions.assertThat(userRatingOptional.get().getPkgVersion().getRevision()).isEqualTo(4);
        }

    }

    /**
     * <p>This will just do a very basic search test; can add others later if/when problems arise.</p>
     */

    @Test
    public void testSearchUserRatings() {

        integrationTestSupportService.createStandardTestData();
        integrationTestSupportService.createUserRatings();

        SearchUserRatingsRequestEnvelope request = new SearchUserRatingsRequestEnvelope()
                .pkgName("pkg3")
                .offset(0)
                .repositoryCode("testrepo")
                .limit(10000)
                .daysSinceCreated(10);

        // ------------------------------------
        SearchUserRatingsResult result = userRatingApiService.searchUserRatings(request);
        // ------------------------------------

        // there are three user ratings, but one is disabled so we will not see that one.
        Assertions.assertThat(result.getItems()).hasSize(3);

        {
            List<SearchUserRatingsResultTotalsByRatingInner> totalsByRating  = result.getTotalsByRating();
            Assertions.assertThat(totalsByRating).hasSize(3);
            Assertions.assertThat(totalsByRating
                    .stream()
                    .filter(tbr -> tbr.getRating() == 3)
                    .findFirst()
                    .map(SearchUserRatingsResultTotalsByRatingInner::getTotal)
                    .orElseThrow()).isEqualTo(1L);
            Assertions.assertThat(totalsByRating
                    .stream()
                    .filter(tbr -> tbr.getRating() == 5)
                    .findFirst()
                    .map(SearchUserRatingsResultTotalsByRatingInner::getTotal)
                    .orElseThrow()).isEqualTo(1L);
            Assertions.assertThat(totalsByRating
                    .stream()
                    .filter(tbr -> tbr.getRating() == -1)
                    .findFirst()
                    .map(SearchUserRatingsResultTotalsByRatingInner::getTotal)
                    .orElseThrow()).isEqualTo(1L);
        }

        {
            SearchUserRatingsResultItemsInner userRating = result.getItems()
                    .stream()
                    .filter(i -> i.getCode().equals("ABCDEF"))
                    .collect(SingleCollector.single());

            Assertions.assertThat(userRating.getActive()).isTrue();
            Assertions.assertThat(userRating.getComment()).isEqualTo("Southern hemisphere winter");
            Assertions.assertThat(userRating.getCreateTimestamp()).isNotNull();
            Assertions.assertThat(userRating.getModifyTimestamp()).isNotNull();
            Assertions.assertThat(userRating.getNaturalLanguageCode()).isEqualTo("en");
            Assertions.assertThat(userRating.getPkgVersion().getPkg().getName()).isEqualTo("pkg3");
            Assertions.assertThat(userRating.getPkgVersion().getRepositoryCode()).isEqualTo("testrepo");
            Assertions.assertThat(userRating.getPkgVersion().getRepositorySourceCode()).isEqualTo("testreposrc_xyz");
            Assertions.assertThat(userRating.getPkgVersion().getArchitectureCode()).isEqualTo("x86_64");
            Assertions.assertThat(userRating.getPkgVersion().getMajor()).isEqualTo("1");
            Assertions.assertThat(userRating.getPkgVersion().getMicro()).isEqualTo("2");
            Assertions.assertThat(userRating.getPkgVersion().getRevision()).isEqualTo(3);
            Assertions.assertThat(userRating.getPkgVersion().getMinor()).isEqualTo("1");
            Assertions.assertThat(userRating.getPkgVersion().getPreRelease()).isNull();
            Assertions.assertThat(userRating.getRating()).isEqualTo((short) 5);
            Assertions.assertThat(userRating.getUser().getNickname()).isEqualTo("urtest1");
            Assertions.assertThat(userRating.getUserRatingStabilityCode()).isNull();
        }

        {
            SearchUserRatingsResultItemsInner userRating = result.getItems()
                    .stream()
                    .filter(i -> i.getCode().equals("GHIJKL"))
                    .collect(SingleCollector.single());

            Assertions.assertThat(userRating.getActive()).isTrue();
            Assertions.assertThat(userRating.getComment()).isEqualTo("Winter banana apples");
            Assertions.assertThat(userRating.getUser().getNickname()).isEqualTo("urtest2");
            Assertions.assertThat(userRating.getUserRatingStabilityCode()).isEqualTo(UserRatingStability.CODE_UNSTABLEBUTUSABLE);
        }

    }

    @Test
    public void testRemoveUserRatings() {

        integrationTestSupportService.createStandardTestData();
        integrationTestSupportService.createUserRatings();
        // note that the user here has not agreed to the user usage conditions,
        // but this is OK because the user is still able to delete a user
        // rating.

        setAuthenticatedUser("urtest2");

        RemoveUserRatingRequestEnvelope request = new RemoveUserRatingRequestEnvelope()
                .code("GHIJKL");

        // ------------------------------------
        userRatingApiService.removeUserRating(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<UserRating> userRatingOptional = UserRating.tryGetByCode(context, "GHIJKL");
            Assertions.assertThat(userRatingOptional.isPresent()).isFalse();
        }

    }

}
