/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.collect.ImmutableSet;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api1.model.authorization.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.api1.model.authorization.AuthorizationTargetType;
import org.haiku.haikudepotserver.api1.model.authorization.CheckAuthorizationRequest;
import org.haiku.haikudepotserver.api1.model.authorization.CheckAuthorizationResult;
import org.haiku.haikudepotserver.api1.model.authorization.CreateAuthorizationPkgRuleRequest;
import org.haiku.haikudepotserver.api1.model.authorization.RemoveAuthorizationPkgRuleRequest;
import org.haiku.haikudepotserver.api1.model.authorization.SearchAuthorizationPkgRulesRequest;
import org.haiku.haikudepotserver.api1.model.authorization.SearchAuthorizationPkgRulesResult;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.PermissionUserPkg;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@ContextConfiguration(classes = TestConfig.class)
public class AuthorizationApiIT extends AbstractIntegrationTest {

    @Resource
    AuthorizationApi authorizationApi;

    private void assertTargetAndPermission(
            IntegrationTestSupportService.StandardTestData data,
            CheckAuthorizationResult.AuthorizationTargetAndPermission targetAndPermission,
            boolean result) {
        Assertions.assertThat(targetAndPermission.permissionCode).isEqualTo(Permission.PKG_EDITICON.name());
        Assertions.assertThat(targetAndPermission.targetIdentifier).isEqualTo(data.pkg1.getName());
        Assertions.assertThat(targetAndPermission.targetType).isEqualTo(AuthorizationTargetType.PKG);
        Assertions.assertThat(targetAndPermission.authorized).isEqualTo(result);
    }

    @Test
    public void checkAuthorizationRequest_asUnauthenticated() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        CheckAuthorizationRequest request = new CheckAuthorizationRequest();
        request.targetAndPermissions = new ArrayList<>();

        request.targetAndPermissions.add(new CheckAuthorizationRequest.AuthorizationTargetAndPermission(
                AuthorizationTargetType.PKG,
                data.pkg1.getName(),
                Permission.PKG_EDITICON.name()));

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApi.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.targetAndPermissions.size()).isEqualTo(1);
        assertTargetAndPermission(data, result.targetAndPermissions.get(0), false);

    }

    // TODO : when some more sophisticated cases are available; implement some better tests
    @Test
    public void checkAuthorizationRequest_asRoot() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        setAuthenticatedUserToRoot();

        CheckAuthorizationRequest request = new CheckAuthorizationRequest();
        request.targetAndPermissions = new ArrayList<>();

        request.targetAndPermissions.add(new CheckAuthorizationRequest.AuthorizationTargetAndPermission(
                AuthorizationTargetType.PKG,
                data.pkg1.getName(),
                Permission.PKG_EDITICON.name()));

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApi.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.targetAndPermissions.size()).isEqualTo(1);
        assertTargetAndPermission(data, result.targetAndPermissions.get(0), true);

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

        CheckAuthorizationRequest request = new CheckAuthorizationRequest();
        request.targetAndPermissions = new ArrayList<>();

        request.targetAndPermissions.add(new CheckAuthorizationRequest.AuthorizationTargetAndPermission(
                AuthorizationTargetType.PKG,
                "pkg1",
                Permission.PKG_EDITICON.name()));

        setAuthenticatedUser("testuser1");

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApi.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.targetAndPermissions.size()).isEqualTo(1);
        Assertions.assertThat(result.targetAndPermissions.get(0).authorized).isFalse();

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
            Pkg pkg1 = Pkg.tryGetByName(context, "pkg1").get();

            org.haiku.haikudepotserver.dataobjects.Permission permission =
                    org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()).get();

            PermissionUserPkg pup_u1p1 = context.newObject(PermissionUserPkg.class);
            pup_u1p1.setPkg(pkg1);
            pup_u1p1.setUser(user1);
            pup_u1p1.setPermission(permission);

            context.commitChanges();
        }

        CheckAuthorizationRequest request = new CheckAuthorizationRequest();
        request.targetAndPermissions = new ArrayList<>();

        request.targetAndPermissions.add(new CheckAuthorizationRequest.AuthorizationTargetAndPermission(
                AuthorizationTargetType.PKG,
                "pkg1",
                Permission.PKG_EDITICON.name()));

        setAuthenticatedUser("testuser1");

        // ------------------------------------
        CheckAuthorizationResult result = authorizationApi.checkAuthorization(request);
        // ------------------------------------

        Assertions.assertThat(result.targetAndPermissions.size()).isEqualTo(1);
        Assertions.assertThat(result.targetAndPermissions.get(0).authorized).isTrue();

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

            CreateAuthorizationPkgRuleRequest request = new CreateAuthorizationPkgRuleRequest();
            request.userNickname = "testuser";
            request.permissionCode = Permission.PKG_EDITICON.name().toLowerCase();
            request.pkgName = "pkg1";

            // ------------------------------------
            authorizationApi.createAuthorizationPkgRule(request);
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

            CreateAuthorizationPkgRuleRequest request = new CreateAuthorizationPkgRuleRequest();
            request.permissionCode = Permission.PKG_EDITSCREENSHOT.name().toLowerCase();
            request.pkgName = "pkg1";
            request.userNickname = "testuser";

            // ------------------------------------
            authorizationApi.createAuthorizationPkgRule(request);
            // ------------------------------------

            // expected exception
        });
    }

    @Test
    public void testCreateAuthorizationRule_permissionUserPkg() throws AuthorizationRuleConflictException {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context,"testuser","fakepassword");
        }

        CreateAuthorizationPkgRuleRequest request = new CreateAuthorizationPkgRuleRequest();
        request.permissionCode = Permission.PKG_EDITSCREENSHOT.name().toLowerCase();
        request.pkgName = "pkg1";
        request.userNickname = "testuser";

        // ------------------------------------
        authorizationApi.createAuthorizationPkgRule(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<PermissionUserPkg> permissionUserPkgOptional =
                    PermissionUserPkg.getByPermissionUserAndPkg(
                            context,
                            org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITSCREENSHOT.name().toLowerCase()).get(),
                            User.tryGetByNickname(context, "testuser").get(),
                            Pkg.tryGetByName(context, "pkg1").get());

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

        CreateAuthorizationPkgRuleRequest request = new CreateAuthorizationPkgRuleRequest();
        request.permissionCode = Permission.PKG_EDITSCREENSHOT.name().toLowerCase();
        request.pkgName = "pkg1";
        request.userNickname = "testuser";

        try {
            authorizationApi.createAuthorizationPkgRule(request);
        }
        catch(AuthorizationRuleConflictException e) {
            throw new RuntimeException("was not expecting the first authorization rule creation to fail",e);
        }

        try {

            // ------------------------------------
            authorizationApi.createAuthorizationPkgRule(request);
            // ------------------------------------

            org.junit.jupiter.api.Assertions.fail("expected an " + AuthorizationRuleConflictException.class.getSimpleName());

        }
        catch(AuthorizationRuleConflictException e) {
            // expected
        }
        catch(Throwable th) {
            org.junit.jupiter.api.Assertions.fail("expected an " + AuthorizationRuleConflictException.class.getSimpleName() + ", but got an instance of " + th.getClass().getSimpleName());
        }

    }

    /**
     * <p>Checks to make sure that any permission that is assigned is only for packages as the target type.  Other
     * sorts of targets are not allowed to be used.</p>
     */

    @Test
    public void testCreateAuthorizationRule_badPermission() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateAuthorizationPkgRuleRequest request = new CreateAuthorizationPkgRuleRequest();
        request.permissionCode = Permission.REPOSITORY_VIEW.name().toLowerCase();
        request.pkgName = "pkg1";
        request.userNickname = "testuser";

        try {
            // ------------------------------------
            authorizationApi.createAuthorizationPkgRule(request);
            // ------------------------------------

            org.junit.jupiter.api.Assertions.fail("expected an "+IllegalStateException.class.getSimpleName()+" to be thrown.");
        }
        catch(IllegalStateException ise) {
            // expected
        }
        catch(Throwable th) {
            org.junit.jupiter.api.Assertions.fail("expected an "+IllegalStateException.class.getSimpleName()+", but got an "+th.getClass().getSimpleName()+" thrown instead");
        }
    }

    @Test
    public void testRemoveAuthorizationRule_permissionUserPkg() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "fakepassword");
            PermissionUserPkg permissionUserPkg = context.newObject(PermissionUserPkg.class);
            permissionUserPkg.setPermission(org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()).get());
            permissionUserPkg.setUser(user);
            permissionUserPkg.setPkg(Pkg.tryGetByName(context, "pkg1").get());
            context.commitChanges();
        }

        RemoveAuthorizationPkgRuleRequest request = new RemoveAuthorizationPkgRuleRequest();
        request.userNickname = "testuser";
        request.permissionCode = Permission.PKG_EDITICON.name().toLowerCase();
        request.pkgName = "pkg1";

        // ------------------------------------
        authorizationApi.removeAuthorizationPkgRule(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(
                    PermissionUserPkg.getByPermissionUserAndPkg(
                            context,
                            org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()).get(),
                            User.tryGetByNickname(context, "testuser").get(),
                            Pkg.tryGetByName(context, "pkg1").get()).isPresent()).isFalse();
        }

    }

    private void createSearchAuthorizationRuleTestData() {
        ObjectContext context = serverRuntime.newContext();

        User user1 = integrationTestSupportService.createBasicUser(context, "testuser1", "fakepassword");
        User user2 = integrationTestSupportService.createBasicUser(context, "testuser2", "fakepassword");
        User user3 = integrationTestSupportService.createBasicUser(context, "testuser3", "fakepassword");

        Pkg pkg1 = Pkg.tryGetByName(context, "pkg1").get();
        Pkg pkg2 = Pkg.tryGetByName(context, "pkg2").get();

        org.haiku.haikudepotserver.dataobjects.Permission permission =
                org.haiku.haikudepotserver.dataobjects.Permission.getByCode(context, Permission.PKG_EDITICON.name().toLowerCase()).get();

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

        SearchAuthorizationPkgRulesRequest request = new SearchAuthorizationPkgRulesRequest();
        request.userNickname = "testuser2";
        request.pkgName = null;
        request.offset = 0;
        request.limit = 10;

        // ------------------------------------
        SearchAuthorizationPkgRulesResult result = authorizationApi.searchAuthorizationPkgRules(request);
        // ------------------------------------

        {
            Assertions.assertThat(result.total).isEqualTo(2);
            Assertions.assertThat(result.items.size()).isEqualTo(2);

            Assertions.assertThat(result.items.stream().map(o -> o.pkgName).collect(Collectors.toSet()))
                .isEqualTo(ImmutableSet.of("pkg1", "pkg2"));
        }

    }

    @Test
    public void testSearchAuthorizationRule_pkg() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();
        createSearchAuthorizationRuleTestData();

        SearchAuthorizationPkgRulesRequest request = new SearchAuthorizationPkgRulesRequest();
        request.userNickname = null;
        request.pkgName = "pkg1";
        request.offset = 0;
        request.limit = 10;

        // ------------------------------------
        SearchAuthorizationPkgRulesResult result = authorizationApi.searchAuthorizationPkgRules(request);
        // ------------------------------------

        {
            Assertions.assertThat(result.total).isEqualTo(3);
            Assertions.assertThat(result.items.size()).isEqualTo(3);

            Assertions.assertThat(result.items.stream().map(o -> o.userNickname).collect(Collectors.toSet()))
                .isEqualTo(ImmutableSet.of("testuser1", "testuser2", "testuser3"));
        }

    }

}
