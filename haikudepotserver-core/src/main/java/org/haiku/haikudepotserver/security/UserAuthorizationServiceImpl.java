/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._UserUsageConditions;
import org.haiku.haikudepotserver.dataobjects.auto._UserUsageConditionsAgreement;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haiku.haikudepotserver.security.model.UserAuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.TargetType;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserAuthorizationServiceImpl implements UserAuthorizationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserAuthorizationServiceImpl.class);

    private TargetType deriveTargetType(DataObject dataObject) {
        if(null==dataObject)
            return null;

        if(User.class.isAssignableFrom(dataObject.getClass())) {
            return TargetType.USER;
        }

        if(Pkg.class.isAssignableFrom(dataObject.getClass())) {
            return TargetType.PKG;
        }

        if(Repository.class.isAssignableFrom(dataObject.getClass())) {
            return TargetType.REPOSITORY;
        }

        if(UserRating.class.isAssignableFrom(dataObject.getClass())) {
            return TargetType.USERRATING;
        }

        throw new IllegalStateException("the data object type '"+dataObject.getClass().getSimpleName()+"' is not handled");
    }

    /**
     * <p>Returns true if the user supplied has the permission over the target object.</p>
     */

    public boolean check(
            ObjectContext objectContext,
            User authenticatedUser,
            TargetType targetType,
            String targetIdentifier,
            Permission permission) {

        Preconditions.checkArgument(null != permission, "the permission must be provided");
        Preconditions.checkArgument(null != objectContext, "the object context must be provided");

        DataObject target = null;

        if (null != targetType) {

            if (Strings.isNullOrEmpty(targetIdentifier)) {
                throw new IllegalStateException("the target type is supplied, but no target identifier");
            }

            Optional<? extends DataObject> targetOptional = switch (targetType) {
                case PKG -> Pkg.tryGetByName(objectContext, targetIdentifier);
                case REPOSITORY -> Repository.tryGetByCode(objectContext, targetIdentifier);
                case USER -> User.tryGetByNickname(objectContext, targetIdentifier);
                case USERRATING -> UserRating.tryGetByCode(objectContext, targetIdentifier);
                default -> throw new IllegalStateException("the target type is not handled; " + targetType.name());
            };

            // if the object was not able to be found then we should bail-out and say that the permission
            // does not apply.

            if(targetOptional.isEmpty()) {
                return false;
            }

            target = targetOptional.get();
        }
        else {
            if(!Strings.isNullOrEmpty(targetIdentifier)) {
                throw new IllegalStateException("the target type was supplied, but not the target identifier");
            }
        }

        return check(objectContext, authenticatedUser, target, permission);
    }

    private boolean isAuthenticatedUserTarget(
            User authenticatedUser,
            DataObject target) {
        if (null == authenticatedUser || null == target) {
            return false;
        }

        if ((target instanceof User)
            && ((User) target).getNickname()
                .equals(authenticatedUser.getNickname())) {
            return true;
        }

        if ((target instanceof UserRating)
                && ((UserRating) target).getUser().getNickname()
                .equals(authenticatedUser.getNickname())) {
            return true;
        }

        return false;
    }

    /**
     * <p>This method will return true if the permission applies in this situation.</p>
     */

    public boolean check(
            ObjectContext objectContext,
            User authenticatedUser,
            DataObject target,
            final Permission permission) {

        Preconditions.checkArgument(null != permission, "the permission must be provided");
        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        Preconditions.checkArgument(
                deriveTargetType(target) == permission.getRequiredTargetType(),
                "during checking authorization, the target object type " + deriveTargetType(target) + " does not match the expected type "  + permission.getRequiredTargetType() + " for permission " + permission);

        // if the authenticated user is not active then there should not be a situation arising where
        // an authorization check is being made.

        if (null != authenticatedUser && !authenticatedUser.getActive()) {
            throw new IllegalStateException("the authenticated user '"+authenticatedUser.getNickname()+"' is not active and so authorization queries cannot be resolved for them");
        }

        // check permissions for which there is no need to have a UUC when the
        // user is acting upon themselves.  The user should always be able to
        // change their password, view themselves etc...

        if (isAuthenticatedUserTarget(authenticatedUser, target)) {
            switch (permission) {
                case USER_CHANGEPASSWORD,
                        USER_VIEW,
                        USER_AGREE_USAGE_CONDITIONS,
                        USER_EDIT,
                        USERRATING_REMOVE -> { // note; checks the user of the user rating
                    return true;
                }
            }
        }

        // if the user has not agreed to the latest UUC and is not root then
        // they should not be able to do anything else...

        boolean authenticatedUserIsRoot = null != authenticatedUser && authenticatedUser.getIsRoot();

        if (null != authenticatedUser && !authenticatedUserIsRoot) {
            String latestUserUsageConditionsCode = UserUsageConditions.getLatest(objectContext).getCode();
            String userLatestUserUsageConditionsCode = authenticatedUser.tryGetUserUsageConditionsAgreement()
                    .map(_UserUsageConditionsAgreement::getUserUsageConditions)
                    .map(_UserUsageConditions::getCode)
                    .orElse(null);

            if (!latestUserUsageConditionsCode.equals(userLatestUserUsageConditionsCode)) {
                LOGGER.trace(
                        "rejecting authorization for user [{}] owing to stale user usage conditions",
                        authenticatedUser.getNickname());
                return false;
            }
        }

        // it could be that permission is afforded based on rules stored in the user.  Check for
        // this situation first.

        if (null != authenticatedUser) {
            switch (permission) {
                case PKG_EDITICON,
                        PKG_EDITSCREENSHOT,
                        PKG_EDITCATEGORIES,
                        PKG_EDITPROMINENCE,
                        PKG_EDITCHANGELOG,
                        PKG_EDITNATIVEDESKTOP,
                        PKG_EDITLOCALIZATION -> {
                    List<? extends AuthorizationPkgRule> rules = authenticatedUser.getAuthorizationPkgRules((Pkg) target);
                    if (rules
                            .stream()
                            .filter(r -> r.getPermission().getCode().equalsIgnoreCase(permission.name()))
                            .collect(SingleCollector.optional()).isPresent()) {
                        return true;
                    }
                }
            }
        }

        // fall back to application-logic rules.

        switch (permission) {
            case AUTHORIZATION_CONFIGURE -> {
                return authenticatedUserIsRoot;
            }
            case REPOSITORY_EDIT, REPOSITORY_IMPORT -> {
                return authenticatedUserIsRoot;
            }
            case REPOSITORY_VIEW -> {
                Repository repository = (Repository) target;
                return repository.getActive() || authenticatedUserIsRoot;
            }
            case REPOSITORY_LIST -> {
                return true;
            }
            case REPOSITORY_LIST_INACTIVE -> {
                return authenticatedUserIsRoot;
            }
            case REPOSITORY_ADD -> {
                return authenticatedUserIsRoot;
            }
            case USER_VIEWJOBS -> {
                return authenticatedUserIsRoot || isAuthenticatedUserTarget(authenticatedUser, target);
            }
            case USER_LIST -> {
                return authenticatedUserIsRoot;
            }
            case USER_CHANGEPASSWORD, USER_VIEW, USER_AGREE_USAGE_CONDITIONS, USER_EDIT -> {
                // ^^ see above for case where a user is acting upon themselves
                return authenticatedUserIsRoot;
            }
            case PKG_CREATEUSERRATING -> {
                return true;
            }
            case PKG_EDITICON,
                 PKG_EDITSCREENSHOT,
                 PKG_EDITCATEGORIES,
                 PKG_EDITNATIVEDESKTOP,
                 PKG_EDITLOCALIZATION,
                 PKG_EDITCHANGELOG,
                 PKG_EDITPROMINENCE,
                 PKG_EDITVERSION -> {
                return authenticatedUserIsRoot;
            }
            case USERRATING_REMOVE -> {
                // ^^ see above for case covering the user rating author
                return authenticatedUserIsRoot;
            }
            case USERRATING_EDIT -> {
                UserRating userRating = (UserRating) target;
                return null != authenticatedUser && (
                        userRating.getUser().getNickname().equals(authenticatedUser.getNickname())
                                || authenticatedUserIsRoot);
            }
            case USERRATING_DERIVEANDSTOREFORPKG -> {
                return authenticatedUserIsRoot;
            }
            case BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET, BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET, BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT, BULK_PKGSCREENSHOTSPREADSHEETREPORT, BULK_PKGICONSPREADSHEETREPORT, BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET, BULK_PKGICONEXPORTARCHIVE, BULK_PKGSCREENSHOTEXPORTARCHIVE -> {
                return null != authenticatedUser;
            }
            case BULK_PKGSCREENSHOTIMPORTARCHIVE, BULK_PKGICONIMPORTARCHIVE, BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET, BULK_USERRATINGSPREADSHEETREPORT_ALL, BULK_PKGVERSIONPAYLOADDATAPOPULATION -> {
                return authenticatedUserIsRoot;
            }
            case BULK_USERRATINGSPREADSHEETREPORT_PKG -> {
                return null != authenticatedUser;
            }
            case BULK_USERRATINGSPREADSHEETREPORT_USER -> {
                User targetUser = (User) target;
                return null != authenticatedUser &&
                        (authenticatedUserIsRoot
                                || authenticatedUser.getNickname().equals(targetUser.getNickname()));
            }
            case JOBS_VIEW -> {
                return authenticatedUserIsRoot;
            }
            default -> throw new IllegalStateException("unhandled permission; " + permission.name());
        }

    }

}
