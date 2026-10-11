/*
 * Copyright 2022-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRuleSearchSpecification;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRuleService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.TargetType;
import org.haiku.haikudepotserver.support.exception.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.haiku.haikudepotserver.support.exception.ValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component("authorizationApiServiceV2")
public class AuthorizationApiService extends AbstractApiService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(AuthorizationApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final AuthorizationPkgRuleService authorizationPkgRulesService;

    public AuthorizationApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            AuthorizationPkgRuleService authorizationPkgRulesService
    ) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.authorizationPkgRulesService = Preconditions.checkNotNull(authorizationPkgRulesService);
    }

    public CheckAuthorizationResult checkAuthorization(CheckAuthorizationRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        return new CheckAuthorizationResult()
                .targetAndPermissions(
                        CollectionUtils.emptyIfNull(request.getTargetAndPermissions())
                                .stream()
                                .map(tandp -> new AuthorizationTargetAndPermissionResult()
                                        .permission(tandp.getPermission())
                                        .targetIdentifier(tandp.getTargetIdentifier())
                                        .targetType(tandp.getTargetType())
                                        .authorized(hasPermission(tandp)))
                                .collect(Collectors.toList()));
    }

    private boolean hasPermission(AuthorizationTargetAndPermissionRequest tandp) {
        Object permission = PermissionMapper.mapFromApiPermission(tandp.getPermission());
        String targetType = Optional.ofNullable(tandp.getTargetType())
                .map(tt -> TargetType.valueOf(tt.name()))
                .map(Object::toString)
                .orElse(null);
        return permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                tandp.getTargetIdentifier(),
                targetType,
                permission);
    }

    public void createAuthorizationPkgRule(CreateAuthorizationPkgRuleRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(null != request.getPermission(), "the permission code is required");
        Preconditions.checkState(
                PermissionMapper.mapFromApiPermission(request.getPermission())
                        .getRequiredTargetType() == TargetType.PKG,
                "the permission should have a target type of; " + TargetType.PKG);
        Preconditions.checkState(
                !Strings.isNullOrEmpty(request.getUserNickname()),
                "the user nickname must be supplied");

        ensureCanAuthorizationManipulate();

        ObjectContext context = serverRuntime.newContext();
        org.haiku.haikudepotserver.dataobjects.Permission permission
                = ensurePermission(context, PermissionMapper.mapFromApiPermission(request.getPermission()));
        User user = ensureUser(context, request.getUserNickname());

        if (user.getIsRoot()) {
            throw new ValidationException(new ValidationFailure("user", "root"));
        }

        Pkg pkg = Optional.ofNullable(request.getPkgName())
                .map(pn -> ensurePkg(context, pn))
                .orElse(null);

        // now we need to check to make sure that the newly added rule does not conflict with an existing
        // rule.  If this is the case then exception.

        if (authorizationPkgRulesService.wouldConflict(context, user, permission, pkg)) {
            throw new AuthorizationRuleConflictException();
        }

        authorizationPkgRulesService.create(
                context, user,
                permission,
                pkg);

        context.commitChanges();
    }

    void removeAuthorizationPkgRule(RemoveAuthorizationPkgRuleRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(null != request.getPermission(), "the permission is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getUserNickname()),"the user nickname is required");

        ensureCanAuthorizationManipulate();
        ObjectContext context = serverRuntime.newContext();
        org.haiku.haikudepotserver.dataobjects.Permission permission = ensurePermission(
                context,
                PermissionMapper.mapFromApiPermission(request.getPermission()));

        User user = Optional.ofNullable(request.getUserNickname())
                .map(un -> ensureUser(context, un))
                .orElse(null);

        Pkg pkg = Optional.ofNullable(request.getPkgName())
                .map(pn -> ensurePkg(context, pn))
                .orElse(null);

        authorizationPkgRulesService.remove(
                context,
                user,
                permission,
                pkg);

        context.commitChanges();
    }

    SearchAuthorizationPkgRulesResult searchAuthorizationPkgRules(SearchAuthorizationPkgRulesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(null == request.getLimit() || request.getLimit() > 0);
        Preconditions.checkState(null != request.getOffset() && request.getOffset() >= 0);

        ensureCanAuthorizationManipulate();

        final ObjectContext context = serverRuntime.newContext();

        AuthorizationPkgRuleSearchSpecification specification = new AuthorizationPkgRuleSearchSpecification();

        specification.setLimit(request.getLimit());
        specification.setOffset(request.getOffset());
        specification.setIncludeInactive(false);
        specification.setUser(Optional.ofNullable(request.getUserNickname())
                .filter(StringUtils::isNotBlank)
                .map(un -> ensureUser(context, un))
                .orElse(null)
        );
        specification.setPermissions(
                Optional.ofNullable(request.getPermissions())
                        .map(pcs -> CollectionUtils.emptyIfNull(request.getPermissions())
                                .stream()
                                .map(PermissionMapper::mapFromApiPermission)
                                .map(pc -> ensurePermission(context, pc))
                                .collect(Collectors.toList()))
                        .orElse(null));
        specification.setPkg(Optional.ofNullable(request.getPkgName())
                .filter(StringUtils::isNotBlank)
                .map(pn -> ensurePkg(context, pn))
                .orElse(null));

        return new SearchAuthorizationPkgRulesResult()
                .total(authorizationPkgRulesService.total(context, specification))
                .items(authorizationPkgRulesService.search(context, specification)
                        .stream()
                        .map(r -> new SearchAuthorizationPkgRulesResultItem()
                                .permission(
                                        PermissionMapper.mapToApiPermission(PermissionMapper.mapFromDataObjectPermission(r.getPermission())))
                                .userNickname(
                                        Optional.ofNullable(r.getUser())
                                                .map(User::getNickname)
                                                .orElse(null)
                                )
                                .pkgName(
                                        Optional.ofNullable(r.getPkg())
                                                .map(Pkg::getName)
                                                .orElse(null)
                                )
                        )
                        .collect(Collectors.toList())
                );
    }

    private org.haiku.haikudepotserver.dataobjects.Permission ensurePermission(ObjectContext context, Permission permission) {
        return org.haiku.haikudepotserver.dataobjects.Permission.tryGetByCode(context, permission.name().toLowerCase())
                .orElseThrow(() -> new ObjectNotFoundException(
                        org.haiku.haikudepotserver.dataobjects.Permission.class.getSimpleName(),
                        permission.name()));
    }

    private Pkg ensurePkg(ObjectContext context, String name) {
        return Pkg.tryGetByName(context, name)
                .orElseThrow(() -> new ObjectNotFoundException(Pkg.class.getSimpleName(), name));
    }

    private User ensureUser(ObjectContext context, String nickname) {
        return User.tryGetByNickname(context, nickname)
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), nickname));
    }

    /**
     * <P>Checks that the currently authenticated user is able to manipulate the authorization configuration
     * of the system.</P>
     */

    private void ensureCanAuthorizationManipulate() {
        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                org.haiku.haikudepotserver.security.model.Permission.AUTHORIZATION_CONFIGURE)) {
            throw new AccessDeniedException("the user is unable to configure authorization");
        }
    }

}
