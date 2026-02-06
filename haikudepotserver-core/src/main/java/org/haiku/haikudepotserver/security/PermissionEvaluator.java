/*
 * Copyright 2020-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.TargetType;
import org.haiku.haikudepotserver.security.model.UserAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

@Component
public class PermissionEvaluator implements org.springframework.security.access.PermissionEvaluator {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PermissionEvaluator.class);

    private final UserAuthorizationService userAuthorizationService;

    private final ServerRuntime serverRuntime;

    public PermissionEvaluator(
            ServerRuntime serverRuntime,
            UserAuthorizationService userAuthorizationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userAuthorizationService = Preconditions.checkNotNull(userAuthorizationService);
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Object targetDomainObject,
            Object permissionObject) {
        Preconditions.checkArgument(null != permissionObject, "the permission is not provided");

        ObjectContext context = serverRuntime.newContext();
        Permission permission = toPermission(permissionObject);

        if (userAuthorizationService.check(
                context,
                Optional.ofNullable(authentication)
                        .filter(a -> a instanceof UserAuthentication)
                        .filter(Authentication::isAuthenticated)
                        .map(a -> (ObjectId) authentication.getPrincipal())
                        .map(userOid -> User.getByObjectId(context, userOid))
                        .orElse(null),
                (DataObject) targetDomainObject,
                permission)) {
            return true;
        }

        return permission == Permission.REPOSITORY_IMPORT
                && targetDomainObject instanceof Repository
                && Optional.of(targetDomainObject)
                .map(po -> (Repository) po)
                .filter(r -> checkRepositoryImport(authentication, r))
                .isPresent();
    }

    @Override
    public boolean hasPermission(
            Authentication authentication,
            Serializable targetId,
            String targetTypeString,
            Object permissionObject) {
        Preconditions.checkArgument(null != permissionObject, "the permission is not provided");
        Permission permission = toPermission(permissionObject);
        TargetType targetType = Optional.ofNullable(targetTypeString)
                .map(StringUtils::trimToNull)
                .map(TargetType::valueOf)
                .orElse(null);
        ObjectContext context = serverRuntime.newContext();

        if (userAuthorizationService.check(
                context,
                Optional.ofNullable(authentication)
                        .filter(a -> a instanceof UserAuthentication)
                        .filter(Authentication::isAuthenticated)
                        .map(a -> (ObjectId) authentication.getPrincipal())
                        .map(userOid -> User.getByObjectId(context, userOid))
                        .orElse(null),
                targetType,
                Optional.ofNullable(targetId).map(Object::toString).orElse(null),
                permission)) {
            return true;
        }

        return null != targetId
                && targetType == TargetType.REPOSITORY
                && permission == Permission.REPOSITORY_IMPORT
                && Repository.tryGetByCode(context, targetId.toString())
                .filter(r -> checkRepositoryImport(authentication, r))
                .isPresent();
    }

    private Permission toPermission(Object permission) {
        Preconditions.checkArgument(null != permission, "permission is required");
        if (permission instanceof Permission) {
            return (Permission) permission;
        }
        return Permission.valueOf(permission.toString());
    }

    private boolean checkRepositoryImport(Authentication authentication, Repository repository) {
        if (StringUtils.isBlank(repository.getPasswordHash())) {
            return true;
        }
        return Optional.ofNullable(authentication)
                .filter(a -> a instanceof RepositoryAuthentication)
                    // ^^ this has already authenticated against the repo.
                .filter(a -> a.getPrincipal().toString().equals(repository.getCode()))
                .isPresent();
    }

}
