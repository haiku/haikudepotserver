/*
 * Copyright 2019-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.UserAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.stream.Stream;

@ContextConfiguration(classes = TestConfig.class)
public class UserAuthorizationServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private UserAuthorizationService userAuthorizationService;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    /**
     * <p>An authorization check has to be performed in the context of an
     * authenticated entity against some target object and a permission.
     * The permission only works against targets of a specific sort.  For
     * example, it is not possible to check the permission
     * <code>USER_VIEW</code> against a package!</p>
     */

    @Test
    public void testPerformCheckOnWrongTargetType() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            IntegrationTestSupportService.StandardTestData standardTestData
                    = integrationTestSupportService.createStandardTestData();

            {
                ObjectContext context = serverRuntime.newContext();
                User user = integrationTestSupportService
                        .createBasicUser(context, "testuser", "guwfwef67");
                integrationTestSupportService
                        .agreeToUserUsageConditions(context, user);
            }

            {
                ObjectContext context = serverRuntime.newContext();
                User user = User.getByNickname(context, "testuser");

                // ---------------------------------
                userAuthorizationService.check(
                        context,
                        user,
                        standardTestData.pkg1, // <--- package
                        Permission.USER_VIEW // <--- permission that checks a user
                );
                // ---------------------------------
            }

            // throws an exception.
        });
    }

    /**
     * <p>Checks that a user is able to see themselves even if they have no
     * UUC in place.</p>
     */

    @Test
    public void testPerformCheckOnViewSelfWithoutUuc() {
        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService
                    .createBasicUser(context, "testuser", "guwfwef67");
            // note; no agreement with UUC
        }

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByNickname(context, "testuser");

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    user,
                    user,
                    Permission.USER_VIEW
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isTrue();
    }

    /**
     * <p>Checks that a user is not able to see another user.</p>
     */

    @Test
    public void testPerformCheckOnViewOther() {
        Stream.of("testuser", "testuser2")
                .forEach(n -> {
                    ObjectContext context = serverRuntime.newContext();
                    User user = integrationTestSupportService
                            .createBasicUser(context, n, "guwfwef67");
                    integrationTestSupportService.agreeToUserUsageConditions(context, user);
                });

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    User.getByNickname(context, "testuser"),
                    User.getByNickname(context, "testuser2"),
                    Permission.USER_VIEW
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isFalse();
    }

    /**
     * <p>Root can view any user.</p>
     */

    @Test
    public void testPerformCheckOnViewOtherAsRoot() {
        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService
                    .createBasicUser(context, "testuser", "guwfwef67");
            // note; no agreement with UUC
        }

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    User.getByNickname(context, "root"),
                    User.getByNickname(context, "testuser"),
                    Permission.USER_VIEW
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testCreateUserRatingWithLatestUuc() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService
                    .createBasicUser(context, "testuser", "guwfwef67");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
        }

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    User.getByNickname(context, "testuser"),
                    Pkg.getByName(context, "pkg2"),
                    Permission.PKG_CREATEUSERRATING
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isTrue();
    }

    /**
     * <p>The user has to have agreed to the latest UUC in order to be able to
     * create a user rating.  In this case the user has agreed to a UUC, but it
     * is not the latest one.</p>
     */

    @Test
    public void testCreateUserRatingWithStaleUuc() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService
                    .createBasicUser(context, "testuser", "guwfwef67");
            integrationTestSupportService.agreeToUserUsageConditions(context, user);
        }

        {
            ObjectContext context = serverRuntime.newContext();
            UserUsageConditions userUsageConditions = context.newObject(UserUsageConditions.class);
            userUsageConditions.setCode("TESTUUC");
            userUsageConditions.setCopyMarkdown("## Title");
            userUsageConditions.setMinimumAge(42);
            userUsageConditions.setOrdering(
                    UserUsageConditions.getLatest(context).getOrdering() + 1000);
            context.commitChanges();
        }

        // have to clear the caches because otherwise it will not see the later
        // UUC.
        clearCaches();

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    User.getByNickname(context, "testuser"),
                    Pkg.getByName(context, "pkg2"),
                    Permission.PKG_CREATEUSERRATING
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isFalse();
    }

    /**
     * <p>The user has to have agreed to the latest UUC in order to be able to
     * create a user rating.</p>
     */

    @Test
    public void testCreateUserRatingWithoutUuc() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService
                    .createBasicUser(context, "testuser", "guwfwef67");
            // note; no agreement with UUC
        }

        boolean result;

        {
            ObjectContext context = serverRuntime.newContext();

            // ---------------------------------
            result = userAuthorizationService.check(
                    context,
                    User.getByNickname(context, "testuser"),
                    Pkg.getByName(context, "pkg2"),
                    Permission.PKG_CREATEUSERRATING
            );
            // ---------------------------------
        }

        Assertions.assertThat(result).isFalse();
    }

}
