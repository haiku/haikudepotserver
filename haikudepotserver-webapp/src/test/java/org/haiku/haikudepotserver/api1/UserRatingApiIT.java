/*
* Copyright 2014-2016, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api1.model.PkgVersionType;
import org.haiku.haikudepotserver.api1.model.userrating.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class UserRatingApiIT extends AbstractIntegrationTest {

    @Resource
    UserRatingApi userRatingApi;

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    private String createTestUserAndSampleUserRating() {
        ObjectContext context = serverRuntime.getContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "password");

        UserRating userRating = context.newObject(UserRating.class);
        userRating.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH).get());
        userRating.setComment("How now brown cow");
        userRating.setPkgVersion(pkgOrchestrationService.getLatestPkgVersionForPkg(
                context,
                Pkg.getByName(context, "pkg1").get(),
                Repository.getByCode(context, "testrepo").get(),
                Collections.singletonList(Architecture.getByCode(context, "x86").get())).get());
        userRating.setRating((short) 3);
        userRating.setUserRatingStability(UserRatingStability.getByCode(context, UserRatingStability.CODE_VERYUNSTABLE).get());
        userRating.setUser(user);
        context.commitChanges();

        return userRating.getCode();
    }

    private void checkAssertionsOnAbstractGetUserRatingResult(AbstractGetUserRatingResult result) {
        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(Strings.isNullOrEmpty(result.code)).isFalse();
        Assertions.assertThat(result.comment).isEqualTo("How now brown cow");
        Assertions.assertThat(result.naturalLanguageCode).isEqualTo(NaturalLanguage.CODE_SPANISH);
        Assertions.assertThat(result.createTimestamp).isNotNull();
        Assertions.assertThat(result.modifyTimestamp).isNotNull();
        Assertions.assertThat(result.rating).isEqualTo((short) 3);
        Assertions.assertThat(result.user.nickname).isEqualTo("testuser");
        Assertions.assertThat(result.userRatingStabilityCode).isEqualTo(UserRatingStability.CODE_VERYUNSTABLE);
        Assertions.assertThat(result.pkgVersion.pkg.name).isEqualTo("pkg1");
        Assertions.assertThat(result.pkgVersion.architectureCode).isEqualTo("x86");
        Assertions.assertThat(result.pkgVersion.major).isEqualTo("1");
        Assertions.assertThat(result.pkgVersion.micro).isEqualTo("2");
        Assertions.assertThat(result.pkgVersion.minor).isNull();
        Assertions.assertThat(result.pkgVersion.revision).isEqualTo(4);
        Assertions.assertThat(result.pkgVersion.preRelease).isNull();
    }

    @Test
    public void testUpdateUserRating() throws Exception {

        integrationTestSupportService.createStandardTestData();
        String userRatingCode = createTestUserAndSampleUserRating();

        setAuthenticatedUser("testuser");

        UpdateUserRatingRequest request = new UpdateUserRatingRequest();
        request.active = false;
        request.rating = (short) 1;
        request.comment = "Highlighter orange";
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;
        request.code = userRatingCode;
        request.userRatingStabilityCode = UserRatingStability.CODE_MOSTLYSTABLE;
        request.filter = ImmutableList.copyOf(UpdateUserRatingRequest.Filter.values());

        // ------------------------------------
        userRatingApi.updateUserRating(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<UserRating> userRatingOptional = UserRating.getByCode(context, userRatingCode);
            Assertions.assertThat(userRatingOptional.get().getActive()).isFalse();
            Assertions.assertThat(userRatingOptional.get().getRating()).isEqualTo((short) 1);
            Assertions.assertThat(userRatingOptional.get().getComment()).isEqualTo("Highlighter orange");
            Assertions.assertThat(userRatingOptional.get().getNaturalLanguage().getCode()).isEqualTo(NaturalLanguage.CODE_GERMAN);
            Assertions.assertThat(userRatingOptional.get().getUserRatingStability().getCode()).isEqualTo(UserRatingStability.CODE_MOSTLYSTABLE);
        }
    }

    @Test
    public void testGetUserRating() throws Exception {
        integrationTestSupportService.createStandardTestData();
        String userRatingCode = createTestUserAndSampleUserRating();

        // ------------------------------------
        GetUserRatingResult result = userRatingApi.getUserRating(new GetUserRatingRequest(userRatingCode));
        // ------------------------------------

        checkAssertionsOnAbstractGetUserRatingResult(result);
    }

    @Test
    public void testGetUserRatingByUserAndPkgVersion() throws ObjectNotFoundException {
        integrationTestSupportService.createStandardTestData();

        String userRatingCode = createTestUserAndSampleUserRating();

        GetUserRatingByUserAndPkgVersionRequest request = new GetUserRatingByUserAndPkgVersionRequest();
        request.pkgName = "pkg1";
        request.userNickname = "testuser";
        request.pkgVersionArchitectureCode = "x86";
        request.pkgVersionMajor = "1";
        request.pkgVersionMicro = "2";
        request.repositoryCode = "testrepo";
        request.pkgVersionMinor = null;
        request.pkgVersionRevision = 4;
        request.pkgVersionPreRelease = null;

        // ------------------------------------
        GetUserRatingByUserAndPkgVersionResult result = userRatingApi.getUserRatingByUserAndPkgVersion(request);
        // ------------------------------------

        Assertions.assertThat(result.code).isEqualTo(userRatingCode);
        checkAssertionsOnAbstractGetUserRatingResult(result);
    }


    @Test
    public void testCreateUserRating() throws Exception {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.getContext();
            integrationTestSupportService.createBasicUser(context, "testuser", "password");
        }

        setAuthenticatedUser("testuser");

        CreateUserRatingRequest request = new CreateUserRatingRequest();
        request.naturalLanguageCode = NaturalLanguage.CODE_SPANISH;
        request.userNickname = "testuser";
        request.repositoryCode = "testrepo";
        request.userRatingStabilityCode = UserRatingStability.CODE_VERYUNSTABLE;
        request.comment = "The supermarket has gone crazy";
        request.rating = (short) 5;
        request.pkgName = "pkg1";
        request.pkgVersionArchitectureCode = "x86";
        request.pkgVersionType = PkgVersionType.LATEST;

        // ------------------------------------
        String code = userRatingApi.createUserRating(request).code;
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<UserRating> userRatingOptional = UserRating.getByCode(context, code);

            Assertions.assertThat(userRatingOptional.isPresent()).isTrue();
            Assertions.assertThat(userRatingOptional.get().getActive()).isTrue();
            Assertions.assertThat(userRatingOptional.get().getComment()).isEqualTo("The supermarket has gone crazy");
            Assertions.assertThat(userRatingOptional.get().getNaturalLanguage().getCode()).isEqualTo(NaturalLanguage.CODE_SPANISH);
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
    public void testSearchUserRatings() throws ObjectNotFoundException {

        integrationTestSupportService.createStandardTestData();
        integrationTestSupportService.createUserRatings();

        SearchUserRatingsRequest request = new SearchUserRatingsRequest();
        request.pkgName = "pkg3";
        request.offset = 0;
        request.repositoryCode = "testrepo";
        request.limit = Integer.MAX_VALUE;
        request.daysSinceCreated = 10;

        // ------------------------------------
        SearchUserRatingsResult result = userRatingApi.searchUserRatings(request);
        // ------------------------------------

        // there are three user ratings, but one is disabled so we will not see that one.
        Assertions.assertThat(result.items.size()).isEqualTo(2);

        {
            SearchUserRatingsResult.UserRating userRating = result.items.stream().filter(i -> i.code.equals("ABCDEF")).collect(SingleCollector.single());
            Assertions.assertThat(userRating.active).isTrue();
            Assertions.assertThat(userRating.comment).isEqualTo("Southern hemisphere winter");
            Assertions.assertThat(userRating.createTimestamp).isNotNull();
            Assertions.assertThat(userRating.modifyTimestamp).isNotNull();
            Assertions.assertThat(userRating.naturalLanguageCode).isEqualTo(NaturalLanguage.CODE_ENGLISH);
            Assertions.assertThat(userRating.pkgVersion.pkg.name).isEqualTo("pkg3");
            Assertions.assertThat(userRating.pkgVersion.repositoryCode).isEqualTo("testrepo");
            Assertions.assertThat(userRating.pkgVersion.architectureCode).isEqualTo("x86");
            Assertions.assertThat(userRating.pkgVersion.major).isEqualTo("1");
            Assertions.assertThat(userRating.pkgVersion.micro).isEqualTo("2");
            Assertions.assertThat(userRating.pkgVersion.revision).isEqualTo(3);
            Assertions.assertThat(userRating.pkgVersion.minor).isEqualTo("1");
            Assertions.assertThat(userRating.pkgVersion.preRelease).isNull();
            Assertions.assertThat(userRating.rating).isEqualTo((short) 5);
            Assertions.assertThat(userRating.user.nickname).isEqualTo("urtest1");
            Assertions.assertThat(userRating.userRatingStabilityCode).isNull();
        }

        {
            SearchUserRatingsResult.UserRating userRating = result.items
                    .stream()
                    .filter(i -> i.code.equals("GHIJKL"))
                    .collect(SingleCollector.single());
            Assertions.assertThat(userRating.active).isTrue();
            Assertions.assertThat(userRating.comment).isEqualTo("Winter banana apples");
            Assertions.assertThat(userRating.user.nickname).isEqualTo("urtest2");
            Assertions.assertThat(userRating.userRatingStabilityCode).isEqualTo(UserRatingStability.CODE_UNSTABLEBUTUSABLE);
        }

    }

    public static class ResultUserRatingFunction implements Predicate<SearchUserRatingsResult.UserRating> {

        private String code;

        public ResultUserRatingFunction(String code) {
            super();
            this.code = code;
        }

        @Override
        public boolean apply(SearchUserRatingsResult.UserRating input) {
            return input.code.equals(code);
        }
    }

}