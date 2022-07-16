/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.google.common.collect.ImmutableSet;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api2.model.AuthorizationTargetAndPermissionRequest;
import org.haiku.haikudepotserver.api2.model.AuthorizationTargetAndPermissionResult;
import org.haiku.haikudepotserver.api2.model.AuthorizationTargetType;
import org.haiku.haikudepotserver.api2.model.CheckAuthorizationRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CheckAuthorizationResult;
import org.haiku.haikudepotserver.api2.model.CreateAuthorizationPkgRuleRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveAuthorizationPkgRuleRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchAuthorizationPkgRulesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchAuthorizationPkgRulesResult;
import org.haiku.haikudepotserver.api2.model.SearchAuthorizationPkgRulesResultItem;
import org.haiku.haikudepotserver.api2.support.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.PermissionUserPkg;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ContextConfiguration(classes = TestConfig.class)
public class AuthorizationApiServiceIT extends AbstractIntegrationTest {

    @Resource
    AuthorizationApiService authorizationApiService;

    private void assertTargetAndPermission(
            IntegrationTestSupportService.StandardTestData data,
            AuthorizationTargetAndPermissionResult targetAndPermission,
            boolean result) {
        Assertions.assertThat(targetAndPermission.getPermissionCode()).isEqualTo(Permission.PKG_EDITICON.name());
        Assertions.assertThat(targetAndPermission.getTargetIdentifier()).isEqualTo(data.pkg1.getName());
        Assertions.assertThat(targetAndPermission.getTargetType()).isEqualTo(AuthorizationTargetType.PKG);
        Assertions.assertThat(targetAndPermission.getAuthorized()).isEqualTo(result);
    }

    @Test
    public void checkAuthorizationRequest_asUnauthenticated() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        CheckAuthorizationRequestEnvelope request = new CheckAuthorizationRequestEnvelope()
                .targetAndPermissions(List.of(
                        new AuthorizationTargetAndPermissionRequest()
                                .targetType(AuthorizationTargetType.PKG)
                                .targetIdentifier(data.pkg1.getName())
                                .permissionCode(Permission.PKG_EDITICON.name())));

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApiService.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.getTargetAndPermissions().size()).isEqualTo(1);
        assertTargetAndPermission(data, result.getTargetAndPermissions().get(0), false);
    }

    // TODO : when some more sophisticated cases are available; implement some better tests
    @Test
    public void checkAuthorizationRequest_asRoot() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        setAuthenticatedUserToRoot();

        CheckAuthorizationRequestEnvelope request = new CheckAuthorizationRequestEnvelope()
                .targetAndPermissions(List.of(
                        new AuthorizationTargetAndPermissionRequest()
                                .targetType(AuthorizationTargetType.PKG)
                                .targetIdentifier(data.pkg1.getName())
                                .permissionCode(Permission.PKG_EDITICON.name())));

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApiService.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.getTargetAndPermissions().size()).isEqualTo(1);
        assertTargetAndPermission(data, result.getTargetAndPermissions().get(0), true);
    }

    /**
     * <P>With a user-pkg rule missing we should see this authorization come through in a check
     * for that permission against the package being false.</P>
     */

    @Test
    public void checkAuthorizationRequest_asUserWithoutRule() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "testuser1", "fakepassword");
        }

        CheckAuthorizationRequestEnvelope request = new CheckAuthorizationRequestEnvelope()
                .targetAndPermissions(List.of(
                        new AuthorizationTargetAndPermissionRequest()
                                .targetType(AuthorizationTargetType.PKG)
                                .targetIdentifier("pkg1")
                                .permissionCode(Permission.PKG_EDITICON.name())));

        setAuthenticatedUser("testuser1");

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApiService.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.getTargetAndPermissions().size()).isEqualTo(1);
        Assertions.assertThat(result.getTargetAndPermissions().get(0).getAuthorized()).isFalse();

    }

    /**
     * <P>With a user-pkg rule in place we should see this authorization come through in a check
     * for that permission against the package being true.</P>
     */

    @Test
    public void checkAuthorizationRequest_asUserWithRule() {
        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            User user1 = integrationTestSupportService.createBasicUser(context, "testuser1", "fakepassword");
            integrationTestSupportService.agreeToUserUsageConditions(context, user1);
            Pkg pkg1 = Pkg.getByName(context, "pkg1");

            org.haiku.haikudepotserver.dataobjects.Permission permission =
                    org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase());

            PermissionUserPkg pup_u1p1 = context.newObject(PermissionUserPkg.class);
            pup_u1p1.setPkg(pkg1);
            pup_u1p1.setUser(user1);
            pup_u1p1.setPermission(permission);

            context.commitChanges();
        }

        CheckAuthorizationRequestEnvelope request = new CheckAuthorizationRequestEnvelope()
                .targetAndPermissions(List.of(
                        new AuthorizationTargetAndPermissionRequest()
                                .targetType(AuthorizationTargetType.PKG)
                                .targetIdentifier("pkg1")
                                .permissionCode(Permission.PKG_EDITICON.name())));

        setAuthenticatedUser("testuser1");

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApiService.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.getTargetAndPermissions().size()).isEqualTo(1);
        Assertions.assertThat(result.getTargetAndPermissions().get(0).getAuthorized()).isTrue();

    }

    /**
     * <p>This test checks to see what happens if you try to create a new rule, but you are not
     * authorized to do so.</p>
     */

    @Test
    public void testCreateAuthorizationRule_unauthorized() {
        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class, () -> {
            integrationTestSupportService.createStandardTestData();

            {
                ObjectContext context = serverRuntime.newContext();
                integrationTestSupportService.createBasicUser(context,"testuser","fakepassword");
            }

            setAuthenticatedUser("testuser");

            CreateAuthorizationPkgRuleRequestEnvelope request = new CreateAuthorizationPkgRuleRequestEnvelope()
                    .userNickname("testuser")
                    .permissionCode(Permission.PKG_EDITICON.name().toLowerCase())
                    .pkgName("pkg1");

            // ------------------------------------
            authorizationApiService.createAuthorizationPkgRule(request);
            // ------------------------------------

            // expected exception.
        });
    }

    /**
     * <p>Checks what happens if you try to create a new rule, but you are not root.</p>
     */

    @Test
    public void testCreateAuthorizationRule_notAuthorized() {
        org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class, () -> {
            integrationTestSupportService.createStandardTestData();

            {
                ObjectContext context = serverRuntime.newContext();
                integrationTestSupportService.createBasicUser(context, "testuser", "fakepassword");
            }

            setAuthenticatedUser("testuser");

            CreateAuthorizationPkgRuleRequestEnvelope request = new CreateAuthorizationPkgRuleRequestEnvelope()
                    .userNickname("testuser")
                    .permissionCode(Permission.PKG_EDITSCREENSHOT.name().toLowerCase())
                    .pkgName("pkg1");

            // ------------------------------------
            authorizationApiService.createAuthorizationPkgRule(request);
            // ------------------------------------

            // expected exception
        });
    }

    @Test
    public void testCreateAuthorizationRule_permissionUserPkg() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context,"testuser","fakepassword");
        }

        CreateAuthorizationPkgRuleRequestEnvelope request = new CreateAuthorizationPkgRuleRequestEnvelope()
                .userNickname("testuser")
                .permissionCode(Permission.PKG_EDITSCREENSHOT.name().toLowerCase())
                .pkgName("pkg1");

        // ------------------------------------
        authorizationApiService.createAuthorizationPkgRule(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<PermissionUserPkg> permissionUserPkgOptional =
                    PermissionUserPkg.getByPermissionUserAndPkg(
                            context,
                            org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITSCREENSHOT.name().toLowerCase()),
                            User.getByNickname(context, "testuser"),
                            Pkg.getByName(context, "pkg1"));

            Assertions.assertThat(permissionUserPkgOptional.isPresent()).isTrue();
        }
    }

    @Test
    public void testCreateAuthorizationRule_conflicting() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context,"testuser","fakepassword");
        }

        CreateAuthorizationPkgRuleRequestEnvelope request = new CreateAuthorizationPkgRuleRequestEnvelope()
                .userNickname("testuser")
                .permissionCode(Permission.PKG_EDITSCREENSHOT.name().toLowerCase())
                .pkgName("pkg1");

        // TODO; do this in another way outside the API!
        authorizationApiService.createAuthorizationPkgRule(request);

        org.junit.jupiter.api.Assertions.assertThrows(
                AuthorizationRuleConflictException.class,
                () -> authorizationApiService.createAuthorizationPkgRule(request)
        );

    }

    /**
     * <p>Checks to make sure that any permission that is assigned is only for packages as the target type.  Other
     * sorts of targets are not allowed to be used.</p>
     */

    @Test
    public void testCreateAuthorizationRule_badPermission() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateAuthorizationPkgRuleRequestEnvelope request = new CreateAuthorizationPkgRuleRequestEnvelope()
                .userNickname("testuser")
                .permissionCode(Permission.REPOSITORY_VIEW.name().toLowerCase())
                .pkgName("pkg1");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> authorizationApiService.createAuthorizationPkgRule(request)
        );
    }

    @Test
    public void testRemoveAuthorizationRule_permissionUserPkg() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "fakepassword");
            PermissionUserPkg permissionUserPkg = context.newObject(PermissionUserPkg.class);
            permissionUserPkg.setPermission(org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()));
            permissionUserPkg.setUser(user);
            permissionUserPkg.setPkg(Pkg.getByName(context, "pkg1"));
            context.commitChanges();
        }

        RemoveAuthorizationPkgRuleRequestEnvelope request = new RemoveAuthorizationPkgRuleRequestEnvelope()
                .userNickname("testuser")
                .permissionCode(Permission.PKG_EDITICON.name().toLowerCase())
                .pkgName("pkg1");

        // ------------------------------------
        authorizationApiService.removeAuthorizationPkgRule(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(
                    PermissionUserPkg.getByPermissionUserAndPkg(
                            context,
                            org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()),
                            User.getByNickname(context, "testuser"),
                            Pkg.getByName(context, "pkg1")).isPresent()).isFalse();
        }

    }

    private void createSearchAuthorizationRuleTestData() {
        ObjectContext context = serverRuntime.newContext();

        User user1 = integrationTestSupportService.createBasicUser(context, "testuser1", "fakepassword");
        User user2 = integrationTestSupportService.createBasicUser(context, "testuser2", "fakepassword");
        User user3 = integrationTestSupportService.createBasicUser(context, "testuser3", "fakepassword");

        Pkg pkg1 = Pkg.getByName(context, "pkg1");
        Pkg pkg2 = Pkg.getByName(context, "pkg2");

        org.haiku.haikudepotserver.dataobjects.Permission permission =
                org.haiku.haikudepotserver.dataobjects.Permission.tryGetByCode(context, Permission.PKG_EDITICON.name().toLowerCase()).get();

        PermissionUserPkg pup_u1p1 = context.newObject(PermissionUserPkg.class);
        pup_u1p1.setPkg(pkg1);
        pup_u1p1.setUser(user1);
        pup_u1p1.setPermission(permission);

        PermissionUserPkg pup_u2p1 = context.newObject(PermissionUserPkg.class);
        pup_u2p1.setPkg(pkg1);
        pup_u2p1.setUser(user2);
        pup_u2p1.setPermission(permission);

        PermissionUserPkg pup_u3p1 = context.newObject(PermissionUserPkg.class);
        pup_u3p1.setPkg(pkg1);
        pup_u3p1.setUser(user3);
        pup_u3p1.setPermission(permission);

        PermissionUserPkg pup_u2p2 = context.newObject(PermissionUserPkg.class);
        pup_u2p2.setPkg(pkg2);
        pup_u2p2.setUser(user2);
        pup_u2p2.setPermission(permission);

        context.commitChanges();
    }

    @Test
    public void testSearchAuthorizationRule_pkgWithUser() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();
        createSearchAuthorizationRuleTestData();

        SearchAuthorizationPkgRulesRequestEnvelope request = new SearchAuthorizationPkgRulesRequestEnvelope()
                .userNickname("testuser2")
                .pkgName(null)
                .offset(0)
                .limit(10);

        // ------------------------------------
        SearchAuthorizationPkgRulesResult result = authorizationApiService.searchAuthorizationPkgRules(request);
        // ------------------------------------

        {
            Assertions.assertThat(result.getTotal()).isEqualTo(2);
            Assertions.assertThat(result.getItems().size()).isEqualTo(2);

            Assertions.assertThat(result.getItems()
                            .stream()
                            .map(SearchAuthorizationPkgRulesResultItem::getPkgName)
                            .collect(Collectors.toSet()))
                .isEqualTo(ImmutableSet.of("pkg1", "pkg2"));
        }

    }

    @Test
    public void testSearchAuthorizationRule_pkg() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();
        createSearchAuthorizationRuleTestData();

        SearchAuthorizationPkgRulesRequestEnvelope request = new SearchAuthorizationPkgRulesRequestEnvelope()
                .userNickname(null)
                .pkgName("pkg1")
                .offset(0)
                .limit(10);

        // ------------------------------------
        SearchAuthorizationPkgRulesResult result = authorizationApiService.searchAuthorizationPkgRules(request);
        // ------------------------------------

        {
            Assertions.assertThat(result.getTotal()).isEqualTo(3);
            Assertions.assertThat(result.getItems().size()).isEqualTo(3);

            Assertions.assertThat(result.getItems()
                            .stream()
                            .map(SearchAuthorizationPkgRulesResultItem::getUserNickname)
                            .collect(Collectors.toSet()))
                .isEqualTo(ImmutableSet.of("testuser1", "testuser2", "testuser3"));
        }

    }

}
