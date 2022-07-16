/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.AgreeUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserResult;
import org.haiku.haikudepotserver.api2.model.ChangePasswordRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CompletePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserResult;
import org.haiku.haikudepotserver.api2.model.GetUserResultUserUsageConditionsAgreement;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsResult;
import org.haiku.haikudepotserver.api2.model.InitiatePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenResult;
import org.haiku.haikudepotserver.api2.model.SearchUsersRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUsersResult;
import org.haiku.haikudepotserver.api2.model.SearchUsersUser;
import org.haiku.haikudepotserver.api2.model.UpdateUserFilter;
import org.haiku.haikudepotserver.api2.model.UpdateUserRequestEnvelope;
import org.haiku.haikudepotserver.support.exception.CaptchaBadResponseException;
import org.haiku.haikudepotserver.support.exception.InvalidUserUsageConditionsException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.haiku.haikudepotserver.support.exception.ValidationFailure;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditionsAgreement;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetService;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.user.model.UserSearchSpecification;
import org.haiku.haikudepotserver.user.model.UserService;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component("userApiServiceV2")
public class UserApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final CaptchaService captchaService;
    private final UserAuthenticationService userAuthenticationService;
    private final UserService userService;
    private final UserRatingService userRatingService;
    private final PasswordResetService passwordResetService;
    private final JobService jobService;

    public UserApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            CaptchaService captchaService,
            UserAuthenticationService userAuthenticationService,
            UserService userService,
            UserRatingService userRatingService,
            PasswordResetService passwordResetService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.captchaService = Preconditions.checkNotNull(captchaService);
        this.userAuthenticationService = Preconditions.checkNotNull(userAuthenticationService);
        this.userService = Preconditions.checkNotNull(userService);
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
        this.passwordResetService = Preconditions.checkNotNull(passwordResetService);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public void agreeUserUsageConditions(AgreeUserUsageConditionsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getNickname()));
        Preconditions.checkArgument(StringUtils.isNotBlank(request.getUserUsageConditionsCode()));

        final ObjectContext context = serverRuntime.newContext();
        User user = User.getByNickname(context, request.getNickname());

        // only the authenticated user is able to agree to the user compliance.

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                user,
                Permission.USER_AGREE_USAGE_CONDITIONS)) {
            throw new AccessDeniedException("unable to agree user usage conditions for user [" + user + "]");
        }

        // remove any existing agreement

        user.getUserUsageConditionsAgreements().forEach(uuca -> uuca.setActive(false));

        UserUsageConditionsAgreement agreement = context.newObject(UserUsageConditionsAgreement.class);
        agreement.setUser(user);
        agreement.setTimestampAgreed();
        agreement.setUserUsageConditions(UserUsageConditions.getByCode(context, request.getUserUsageConditionsCode()));

        context.commitChanges();

        LOGGER.info("did agree to user usage conditions [{}]", request.getUserUsageConditionsCode());
    }

    public AuthenticateUserResult authenticateUser(AuthenticateUserRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        String nickname = StringUtils.trimToNull(request.getNickname());
        String passwordClear = StringUtils.trimToNull(request.getPasswordClear());

        Optional<ObjectId> userOidOptional = userAuthenticationService.authenticateByNicknameAndPassword(nickname, passwordClear);

        if (userOidOptional.isEmpty()) {

            // if the authentication has failed then best to sleep for a moment
            // to make brute forcing a bit more tricky.
            // TODO; this will eat threads; any other way to do it?

            Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
            return new AuthenticateUserResult();
        }

        ObjectContext context = serverRuntime.newContext();
        User user = User.getByObjectId(context, userOidOptional.get());
        return new AuthenticateUserResult().token(userAuthenticationService.generateToken(user));
    }

    public void changePassword(ChangePasswordRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNickname()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNewPasswordClear()));

        // get the logged in and target user.

        final ObjectContext context = serverRuntime.newContext();

        User authUser = obtainAuthenticatedUser(context);

        User targetUser = User.tryGetByNickname(context, request.getNickname()).orElseThrow(
                () -> new ObjectNotFoundException(User.class.getSimpleName(), request.getNickname())
        );

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                targetUser, Permission.USER_CHANGEPASSWORD)) {
            throw new AccessDeniedException(
                    "the logged in user is not allowed to change the password of another user ["
                            + request.getNickname() + "]");
        }

        // if the user is changing their own password then they need to know their existing password
        // first.

        if (!userAuthenticationService.validatePassword(request.getNewPasswordClear())) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // we need to make sure that the captcha is valid.

        if (Strings.isNullOrEmpty(request.getCaptchaToken())) {
            throw new IllegalStateException("the captcha token must be supplied to change the password");
        }

        if (Strings.isNullOrEmpty(request.getCaptchaResponse())) {
            throw new IllegalStateException("the captcha response must be supplied to change the password");
        }

        if (!captchaService.verify(request.getCaptchaToken(), request.getCaptchaResponse())) {
            throw new CaptchaBadResponseException();
        }

        // we need to make sure that the old and new passwords match-up.

        if (targetUser.getNickname().equals(authUser.getNickname())) {
            if (Strings.isNullOrEmpty(request.getOldPasswordClear())) {
                throw new IllegalStateException("the old password clear is required to change the password of a user unless the logged in user is root.");
            }

            if (userAuthenticationService.authenticateByNicknameAndPassword(
                    request.getNickname(),
                    request.getOldPasswordClear()).isEmpty()) {

                // if the old password does not match to the user then we should present this
                // as a validation failure rather than an authorization failure.

                LOGGER.info("the supplied old password is invalid for the user {}", request.getNickname());
                throw new ValidationException(new ValidationFailure("oldPasswordClear", "mismatched"));
            }
        }

        userAuthenticationService.setPassword(targetUser, request.getNewPasswordClear());

        context.commitChanges();
        LOGGER.info("did change password for user {}", request.getNickname());
    }

    public void completePasswordReset(CompletePasswordResetRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCaptchaToken()), "a capture token is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getToken()), "a token is required to facilitate the reset of the password");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPasswordClear()), "a new password is required to reset the password");

        if (!captchaService.verify(request.getCaptchaToken(), request.getCaptchaResponse())) {
            throw new CaptchaBadResponseException();
        }

        try {
            passwordResetService.complete(request.getToken(), request.getPasswordClear());
        }
        catch(Throwable th) {
            LOGGER.error("unable to complete password reset", th);
        }
    }

    public void createUser(CreateUserRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNickname()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPasswordClear()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCaptchaToken()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getCaptchaResponse()),"a capture response is required to create a user");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNaturalLanguageCode()));

        if (!userAuthenticationService.validatePassword(request.getPasswordClear())) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // check the supplied catcha matches the token.

        if (!captchaService.verify(request.getCaptchaToken(), request.getCaptchaResponse())) {
            throw new CaptchaBadResponseException();
        }

        // we need to check the nickname even before we create the user because we have to
        // check for uniqueness of the nickname across all of the users.

        if (Strings.isNullOrEmpty(request.getNickname())) {
            throw new ValidationException(
                    new ValidationFailure(User.NICKNAME.getName(), "required")
            );
        }

        if (StringUtils.isBlank(request.getUserUsageConditionsCode())) {
            throw new InvalidUserUsageConditionsException();
        }

        final ObjectContext context = serverRuntime.newContext();
        String latestUserUsageConditionsCode = UserUsageConditions.getLatest(context).getCode();

        if (!latestUserUsageConditionsCode.equals(request.getUserUsageConditionsCode())) {
            throw new InvalidUserUsageConditionsException();
        }

        UserUsageConditions userUsageConditions = UserUsageConditions.getByCode(
                context, request.getUserUsageConditionsCode());

        //need to check that the nickname is not already in use.

        if (User.tryGetByNickname(context, request.getNickname()).isPresent()) {
            throw new ValidationException(
                    new ValidationFailure(User.NICKNAME.getName(), "notunique")
            );
        }

        User user = context.newObject(User.class);
        user.setNaturalLanguage(getNaturalLanguage(context, request.getNaturalLanguageCode()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());

        userAuthenticationService.setPassword(user, request.getPasswordClear());

        UserUsageConditionsAgreement agreement = context.newObject(UserUsageConditionsAgreement.class);
        agreement.setUser(user);
        agreement.setTimestampAgreed();
        agreement.setUserUsageConditions(userUsageConditions);

        context.commitChanges();

        LOGGER.info("data create user; {}", user.getNickname());
    }

    public GetUserResult getUser(GetUserRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNickname()));

        final ObjectContext context = serverRuntime.newContext();
        User user = User.tryGetByNickname(context, request.getNickname())
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME.getName()));

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                user, Permission.USER_VIEW)) {
            throw new AccessDeniedException("unable to view user [" + user + "]");
        }

        return new GetUserResult()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .isRoot(user.getIsRoot())
                .active(user.getActive())
                .naturalLanguageCode(user.getNaturalLanguage().getCode())
                .createTimestamp(user.getCreateTimestamp().getTime())
                .modifyTimestamp(user.getModifyTimestamp().getTime())
                .lastAuthenticationTimestamp(Optional.ofNullable(user.getLastAuthenticationTimestamp())
                        .map(Date::getTime)
                        .orElse(null))
                .userUsageConditionsAgreement(user.tryGetUserUsageConditionsAgreement()
                        .map(uuca -> new GetUserResultUserUsageConditionsAgreement()
                                .timestampAgreed(uuca.getTimestampAgreed().getTime())
                                .userUsageConditionsCode(uuca.getUserUsageConditions().getCode())
                                .isLatest(uuca.getUserUsageConditions().getCode()
                                        .equals(UserUsageConditions.getLatest(context).getCode())))
                        .orElse(null));
    }

    public GetUserUsageConditionsResult getUserUsageConditions(GetUserUsageConditionsRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();
        UserUsageConditions userUsageConditions = StringUtils.isNotBlank(request.getCode())
                ? UserUsageConditions.getByCode(context, request.getCode())
                : UserUsageConditions.getLatest(context);

        return new GetUserUsageConditionsResult()
                .code(userUsageConditions.getCode())
                .minimumAge(userUsageConditions.getMinimumAge());
    }

    public void initiatePasswordReset(InitiatePasswordResetRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        if (!captchaService.verify(request.getCaptchaToken(), request.getCaptchaResponse())) {
            throw new CaptchaBadResponseException();
        }

        passwordResetService.initiate(request.getEmail());
    }

    public RenewTokenResult renewToken(RenewTokenRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getToken()));

        Optional<ObjectId> userOidOptional = userAuthenticationService.authenticateByToken(request.getToken());
        RenewTokenResult result = new RenewTokenResult();

        if (userOidOptional.isPresent()) {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByObjectId(context, userOidOptional.get());
            result = result.token(userAuthenticationService.generateToken(user));
            LOGGER.debug("did renew token for user; {}", user.toString());
        }
        else {
            LOGGER.info("unable to renew token");
        }

        return result;
    }

    public SearchUsersResult searchUsers(SearchUsersRequestEnvelope request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.newContext();

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.USER_LIST)) {
            throw new AccessDeniedException("unable to list users");
        }

        UserSearchSpecification specification = new UserSearchSpecification();
        specification.setExpression(StringUtils.toRootLowerCase(StringUtils.trimToNull(request.getExpression())));
        specification.setExpressionType(Optional.ofNullable(request.getExpressionType())
                .map(et -> PkgSearchSpecification.ExpressionType.valueOf(et.name()))
                .orElse(null));
        specification.setLimit(request.getLimit());
        specification.setOffset(request.getOffset());
        specification.setIncludeInactive(BooleanUtils.isTrue(request.getIncludeInactive()));

        long total = userService.total(context,specification);
        List<SearchUsersUser> items = Collections.emptyList();

        if (0 != total) {
            List<User> searchedUsers = userService.search(context, specification);
            items = searchedUsers.stream()
                    .map(u -> new SearchUsersUser()
                            .active(u.getActive())
                            .nickname(u.getNickname()))
                    .collect(Collectors.toUnmodifiableList());
        }

        return new SearchUsersResult()
                .total(total)
                .items(items);
    }

    public void updateUser(UpdateUserRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNickname()));
        Preconditions.checkNotNull(request.getFilter());

        final ObjectContext context = serverRuntime.newContext();
        boolean activeDidChange = false;

        User user = User.tryGetByNickname(context, request.getNickname())
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME.getName()));

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                user, Permission.USER_EDIT)) {
            throw new AccessDeniedException("cannot edit [" + user + "]");
        }

        for (UpdateUserFilter filter : request.getFilter()) {

            switch (filter) {

                case NATURALLANGUAGE:
                    if (Strings.isNullOrEmpty(request.getNaturalLanguageCode())) {
                        throw new IllegalStateException("the natural language code is required to update the natural language on a user");
                    }

                    user.setNaturalLanguage(getNaturalLanguage(context, request.getNaturalLanguageCode()));
                    LOGGER.info("will update the natural language on the user {} to {}", user, request.getNaturalLanguageCode());
                    break;

                case EMAIL:
                    user.setEmail(request.getEmail());
                    break;

                case ACTIVE:
                    if (null == request.getActive()) {
                        throw new IllegalStateException("the 'active' attribute is required to configure active on the user.");
                    }

                    activeDidChange = user.getActive() != request.getActive();
                    user.setActive(request.getActive());
                    break;

                default:
                    throw new IllegalStateException("unknown filter in edit user; " + filter.name());
            }
        }

        if (context.hasChanges()) {
            context.commitChanges();
            LOGGER.info("did update the user {}", user.toString());

            // if a user is made active or inactive will have some impact on the user-ratings.

            if (activeDidChange) {
                List<String> pkgNames = userRatingService.pkgNamesEffectedByUserActiveStateChange(
                        context, user);

                LOGGER.info(
                        "will update user rating derivation for {} packages owing to active state change on user {}",
                        pkgNames.size(),
                        user);

                for (String pkgName : pkgNames) {
                    jobService.submit(
                            new UserRatingDerivationJobSpecification(pkgName),
                            JobSnapshot.COALESCE_STATUSES_QUEUED);
                }
            }

        }
        else {
            LOGGER.info("no changes in updating the user {}", user.toString());
        }
    }

}
