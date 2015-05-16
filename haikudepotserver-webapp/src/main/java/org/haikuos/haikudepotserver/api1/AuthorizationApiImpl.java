/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.authorization.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.api1.support.ValidationException;
import org.haikuos.haikudepotserver.api1.support.ValidationFailure;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthorizationPkgRuleOrchestrationService;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRuleSearchSpecification;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.security.model.TargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AuthorizationApiImpl extends AbstractApiImpl implements AuthorizationApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private AuthorizationPkgRuleOrchestrationService authorizationRulesOrchestrationService;

    // -------------------------------
    // HELPERS

    private org.haikuos.haikudepotserver.dataobjects.Permission ensurePermission(ObjectContext context, String code) throws ObjectNotFoundException {

        Optional<org.haikuos.haikudepotserver.dataobjects.Permission> permissionOptional = org.haikuos.haikudepotserver.dataobjects.Permission.getByCode(context, code);

        if(!permissionOptional.isPresent()) {
            throw new ObjectNotFoundException(
                    org.haikuos.haikudepotserver.dataobjects.Permission.class.getSimpleName(),
                    code);
        }

        return permissionOptional.get();
    }

    private Pkg ensurePkg(ObjectContext context, String name) throws ObjectNotFoundException {
        Optional<Pkg> pkgOptional = Pkg.getByName(context, name);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), name);
        }

        return pkgOptional.get();
    }

    private User ensureUser(ObjectContext context, String nickname) throws ObjectNotFoundException {
        Optional<User> userOptional = User.getByNickname(context, nickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), nickname);
        }

        return userOptional.get();
    }

    /**
     * <P>Checks that the currently authenticated user is able to manipulate the authorization configuration
     * of the system.</P>
     */

    private void ensureCanAuthorizationManipulate() {
        ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, null, Permission.AUTHORIZATION_CONFIGURE)) {
            throw new AuthorizationFailureException();
        }
    }

    // -------------------------------
    // API

    @Override
    public CheckAuthorizationResult checkAuthorization(CheckAuthorizationRequest deriveAuthorizationRequest) {

        Preconditions.checkNotNull(deriveAuthorizationRequest);
        Preconditions.checkNotNull(deriveAuthorizationRequest.targetAndPermissions);

        final ObjectContext context = serverRuntime.getContext();
        CheckAuthorizationResult result = new CheckAuthorizationResult();
        result.targetAndPermissions = new ArrayList<>();

        for(CheckAuthorizationRequest.AuthorizationTargetAndPermission targetAndPermission : deriveAuthorizationRequest.targetAndPermissions) {

            CheckAuthorizationResult.AuthorizationTargetAndPermission authorizationTargetAndPermission = new CheckAuthorizationResult.AuthorizationTargetAndPermission();

            authorizationTargetAndPermission.permissionCode = targetAndPermission.permissionCode;
            authorizationTargetAndPermission.targetIdentifier = targetAndPermission.targetIdentifier;
            authorizationTargetAndPermission.targetType = targetAndPermission.targetType;

            authorizationTargetAndPermission.authorized = authorizationService.check(
                    context,
                    tryObtainAuthenticatedUser(context).orElse(null),
                    null!=targetAndPermission.targetType ? TargetType.valueOf(targetAndPermission.targetType.name()) : null,
                    targetAndPermission.targetIdentifier,
                    Permission.valueOf(targetAndPermission.permissionCode));

            result.targetAndPermissions.add(authorizationTargetAndPermission);
        }

        return result;
    }

    @Override
    public CreateAuthorizationPkgRuleResult createAuthorizationPkgRule(CreateAuthorizationPkgRuleRequest request) throws ObjectNotFoundException,AuthorizationRuleConflictException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.permissionCode), "the permission code is required");
        Preconditions.checkState(Permission.valueOf(request.permissionCode.toUpperCase()).getRequiredTargetType() == TargetType.PKG,"the permission should have a target type of; " + TargetType.PKG);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname),"the user nickname must be supplied");

        ensureCanAuthorizationManipulate();
        ObjectContext context = serverRuntime.getContext();
        org.haikuos.haikudepotserver.dataobjects.Permission permission = ensurePermission(context, request.permissionCode);
        User user = ensureUser(context, request.userNickname);

        if(user.getIsRoot()) {
            throw new ValidationException(new ValidationFailure("user", "root"));
        }

        Pkg pkg = null;

        if(null!=request.pkgName) {
            pkg = ensurePkg(context, request.pkgName);
        }

        // now we need to check to make sure that the newly added rule does not conflict with an existing
        // rule.  If this is the case then exception.

        if(authorizationRulesOrchestrationService.wouldConflict(context,user,permission,pkg)) {
            throw new AuthorizationRuleConflictException();
        }

        authorizationRulesOrchestrationService.create(
                context, user,
                permission,
                pkg);

        context.commitChanges();

        return new CreateAuthorizationPkgRuleResult();
    }

    @Override
    public RemoveAuthorizationPkgRuleResult removeAuthorizationPkgRule(RemoveAuthorizationPkgRuleRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.permissionCode), "the permission code is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.userNickname),"the user nickname is required");

        ensureCanAuthorizationManipulate();
        ObjectContext context = serverRuntime.getContext();
        org.haikuos.haikudepotserver.dataobjects.Permission permission = ensurePermission(context, request.permissionCode);
        User user = null;

        if(null!=request.userNickname) {
            user = ensureUser(context, request.userNickname);
        }

        Pkg pkg = null;

        if(!Strings.isNullOrEmpty(request.pkgName)) {
            pkg = ensurePkg(context, request.pkgName);
        }

        authorizationRulesOrchestrationService.remove(
                context,
                user,
                permission,
                pkg);

        context.commitChanges();

        return new RemoveAuthorizationPkgRuleResult();
    }

    @Override
    public SearchAuthorizationPkgRulesResult searchAuthorizationPkgRules(SearchAuthorizationPkgRulesRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(null==request.limit || request.limit > 0);
        Preconditions.checkState(null!=request.offset && request.offset >= 0);

        ensureCanAuthorizationManipulate();

        final ObjectContext context = serverRuntime.getContext();

        AuthorizationPkgRuleSearchSpecification specification = new AuthorizationPkgRuleSearchSpecification();

        specification.setLimit(request.limit);
        specification.setOffset(request.offset);
        specification.setIncludeInactive(false);

        if(!Strings.isNullOrEmpty(request.userNickname)) {
            specification.setUser(ensureUser(context, request.userNickname));
        }

        if(null!=request.permissionCodes) {
            List<org.haikuos.haikudepotserver.dataobjects.Permission> permissions = new ArrayList<>();

            for(int i=0;i<request.permissionCodes.size();i++) {
                permissions.add(ensurePermission(context, request.permissionCodes.get(i)));
            }

            specification.setPermissions(permissions);
        }

        if(!Strings.isNullOrEmpty(request.pkgName)) {
            specification.setPkg(ensurePkg(context, request.pkgName));
        }

        SearchAuthorizationPkgRulesResult result = new SearchAuthorizationPkgRulesResult();
        result.total = authorizationRulesOrchestrationService.total(context, specification);
        result.items = authorizationRulesOrchestrationService.search(context, specification)
                .stream()
                .map(r -> {
                            SearchAuthorizationPkgRulesResult.Rule rule = new SearchAuthorizationPkgRulesResult.Rule();
                            rule.permissionCode = r.getPermission().getCode();
                            rule.userNickname = r.getUser().getNickname();
                            rule.pkgName = null != r.getPkg() ? r.getPkg().getName() : null;
                            return rule;
                        }
                )
                .collect(Collectors.toList());

        return result;
    }


}
