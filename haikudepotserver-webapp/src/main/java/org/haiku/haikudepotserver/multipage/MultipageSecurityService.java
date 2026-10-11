package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.api2.AuthorizationApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.AuthenticationHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// TODO; tie-up with `AbstractUserAuthenticationAware`???
// TODO; remove the `ServerRuntime`???

@Service
public class MultipageSecurityService {

    private final ServerRuntime serverRuntime;

    private final AuthorizationApiService authorizationApiService;

    public MultipageSecurityService(
            ServerRuntime serverRuntime,
            AuthorizationApiService authorizationApiService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authorizationApiService = Preconditions.checkNotNull(authorizationApiService);
    }

    /**
     * <p>This method will call to the authorization API to filter the supplied list of permissions down to
     * those which are allowed without context.</p>
     */
    public Set<Permission> filterPermissions(Collection<Permission> permissions) {
        return filterPermissionsForTarget(null, null, permissions);
    }

    /**
     * <p>This method will call to the authorization API to filter the supplied list of permissions down to
     * those which are allowed for the repository identified by the supplied code.</p>
     */
    public Set<Permission> filterPermissionsForRepository(String code, Collection<Permission> permissions) {
        return filterPermissionsForTarget(AuthorizationTargetType.REPOSITORY, code, permissions);
    }

    /**
     * <P>This method will (optionally) return a user that represents the currently authenticated user.  It will not
     * return null.</P>
     */

    public Optional<org.haiku.haikudepotserver.multipage.model.User> tryObtainAuthenticatedUser() {
        return tryObtainAuthenticatedDataModelUser(serverRuntime.newContext())
                .map(u -> new org.haiku.haikudepotserver.multipage.model.User(u.getNickname()));
    }

    private static Optional<User> tryObtainAuthenticatedDataModelUser(ObjectContext objectContext) {
        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        return AuthenticationHelper.tryGetUserForAuthentication(objectContext, SecurityContextHolder.getContext().getAuthentication());
    }

    private Set<Permission> filterPermissionsForTarget(
            @Nullable AuthorizationTargetType targetType,
            @Nullable String targetIdentifier,
            Collection<Permission> permissions) {
        if (CollectionUtils.isEmpty(permissions)) {
            return Set.of();
        }

        CheckAuthorizationResult result = authorizationApiService.checkAuthorization(
                new CheckAuthorizationRequestEnvelope()
                        .targetAndPermissions(
                                permissions.stream().map(p ->
                                                new AuthorizationTargetAndPermissionRequest()
                                                        .targetType(targetType)
                                                        .targetIdentifier(targetIdentifier)
                                                        .permission(p)
                                        )
                                        .toList()
                        )
        );

        return result.getTargetAndPermissions().stream()
                .filter(AuthorizationTargetAndPermissionResult::getAuthorized)
                .map(AuthorizationTargetAndPermissionResult::getPermission)
                .collect(Collectors.toUnmodifiableSet());
    }

}
