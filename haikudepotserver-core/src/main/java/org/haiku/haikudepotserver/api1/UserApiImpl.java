/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.user.*;
import org.haiku.haikudepotserver.api1.support.*;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetService;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.haiku.haikudepotserver.security.model.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.user.model.UserSearchSpecification;
import org.haiku.haikudepotserver.user.model.UserService;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/user") // TODO; legacy path - remove
public class UserApiImpl extends AbstractApiImpl implements UserApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final AuthorizationService authorizationService;
    private final CaptchaService captchaService;
    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final UserRatingService userRatingService;
    private final PasswordResetService passwordResetService;
    private final JobService jobService;

    public UserApiImpl(
            ServerRuntime serverRuntime,
            AuthorizationService authorizationService,
            CaptchaService captchaService,
            AuthenticationService authenticationService,
            UserService userService,
            UserRatingService userRatingService,
            PasswordResetService passwordResetService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authorizationService = Preconditions.checkNotNull(authorizationService);
        this.captchaService = Preconditions.checkNotNull(captchaService);
        this.authenticationService = Preconditions.checkNotNull(authenticationService);
        this.userService = Preconditions.checkNotNull(userService);
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
        this.passwordResetService = Preconditions.checkNotNull(passwordResetService);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @Override
    public SynchronizeUsersResult synchronizeUsers(SynchronizeUsersRequest synchronizeUsersRequest) {
       Preconditions.checkNotNull(synchronizeUsersRequest);

        final ObjectContext context = serverRuntime.newContext();

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.USER_SYNCHRONIZE)) {
            throw new AuthorizationFailureException();
        }

        return new SynchronizeUsersResult();
    }

    @Override
    public UpdateUserResult updateUser(UpdateUserRequest updateUserRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(updateUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(updateUserRequest.nickname));
        Preconditions.checkNotNull(updateUserRequest.filter);

        final ObjectContext context = serverRuntime.newContext();
        User authUser = obtainAuthenticatedUser(context);
        boolean activeDidChange = false;

        User user = User.tryGetByNickname(context, updateUserRequest.nickname)
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME.getName()));

        if (!authorizationService.check(context, authUser, user, Permission.USER_EDIT)) {
            throw new AuthorizationFailureException();
        }

        for (UpdateUserRequest.Filter filter : updateUserRequest.filter) {

            switch (filter) {

                case NATURALLANGUAGE:

                    if(Strings.isNullOrEmpty(updateUserRequest.naturalLanguageCode)) {
                        throw new IllegalStateException("the natural language code is required to update the natural language on a user");
                    }

                    user.setNaturalLanguage(getNaturalLanguage(context, updateUserRequest.naturalLanguageCode));

                    LOGGER.info("will update the natural language on the user {} to {}", user.toString(), updateUserRequest.naturalLanguageCode);

                    break;

                case EMAIL:
                    user.setEmail(updateUserRequest.email);
                    break;

                case ACTIVE:
                    if(null==updateUserRequest.active) {
                        throw new IllegalStateException("the 'active' attribute is required to configure active on the user.");
                    }

                    activeDidChange = user.getActive() != updateUserRequest.active;
                    user.setActive(updateUserRequest.active);

                    break;

                default:
                    throw new IllegalStateException("unknown filter in edit user; " + filter.name());

            }

        }

        if(context.hasChanges()) {
            context.commitChanges();
            LOGGER.info("did update the user {}", user.toString());

            // if a user is made active or inactive will have some impact on the user-ratings.

            if(activeDidChange) {
                List<String> pkgNames = userRatingService.pkgNamesEffectedByUserActiveStateChange(
                        context, user);

                LOGGER.info(
                        "will update user rating derivation for {} packages owing to active state change on user {}",
                        pkgNames.size(),
                        user.toString());

                for(String pkgName : pkgNames) {
                    jobService.submit(
                            new UserRatingDerivationJobSpecification(pkgName),
                            JobSnapshot.COALESCE_STATUSES_QUEUED);
                }
            }

        }
        else {
            LOGGER.info("no changes in updating the user {}", user.toString());
        }

        return new UpdateUserResult();
    }

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(createUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.passwordClear));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaToken));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaResponse),"a capture response is required to create a user");
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.naturalLanguageCode));

        if(!authenticationService.validatePassword(createUserRequest.passwordClear)) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // check the supplied catcha matches the token.

        if(!captchaService.verify(createUserRequest.captchaToken, createUserRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        // we need to check the nickname even before we create the user because we have to
        // check for uniqueness of the nickname across all of the users.

        if(Strings.isNullOrEmpty(createUserRequest.nickname)) {
            throw new ValidationException(
                    new ValidationFailure(
                            User.NICKNAME.getName(), "required")
            );
        }

        final ObjectContext context = serverRuntime.newContext();

        //need to check that the nickname is not already in use.

        if(User.tryGetByNickname(context,createUserRequest.nickname).isPresent()) {
            throw new ValidationException(
                    new ValidationFailure(
                            User.NICKNAME.getName(), "notunique")
            );
        }

        User user = context.newObject(User.class);
        user.setNaturalLanguage(getNaturalLanguage(context, createUserRequest.naturalLanguageCode));
        user.setNickname(createUserRequest.nickname);
        user.setPasswordSalt(); // random
        user.setEmail(createUserRequest.email);
        user.setPasswordHash(authenticationService.hashPassword(user, createUserRequest.passwordClear));
        context.commitChanges();

        LOGGER.info("data create user; {}", user.getNickname());

        return new CreateUserResult();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getUserRequest.nickname));

        final ObjectContext context = serverRuntime.newContext();
        User authUser = obtainAuthenticatedUser(context);

        User user = User.tryGetByNickname(context, getUserRequest.nickname)
                .orElseThrow(() -> new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME.getName()));

        if(!authorizationService.check(context, authUser, user, Permission.USER_VIEW)) {
            throw new AuthorizationFailureException();
        }

        GetUserResult result = new GetUserResult();
        result.nickname = user.getNickname();
        result.email = user.getEmail();
        result.isRoot = user.getIsRoot();
        result.active = user.getActive();
        result.naturalLanguageCode = user.getNaturalLanguage().getCode();
        result.createTimestamp = user.getCreateTimestamp().getTime();
        result.modifyTimestamp = user.getModifyTimestamp().getTime();
        return result;
    }

    // TODO; some sort of brute-force checking here; too many authentication requests in a short period; go into lock-down?

    @Override
    public AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest) {
        Preconditions.checkNotNull(authenticateUserRequest);
        AuthenticateUserResult authenticateUserResult = new AuthenticateUserResult();
        authenticateUserResult.token = null;

        if (null != authenticateUserRequest.nickname) {
            authenticateUserRequest.nickname = authenticateUserRequest.nickname.trim();
        }

        if (null != authenticateUserRequest.passwordClear) {
            authenticateUserRequest.passwordClear = authenticateUserRequest.passwordClear.trim();
        }

        Optional<ObjectId> userOidOptional = authenticationService.authenticateByNicknameAndPassword(
                authenticateUserRequest.nickname,
                authenticateUserRequest.passwordClear);

        if(!userOidOptional.isPresent()) {

            // if the authentication has failed then best to sleep for a moment
            // to make brute forcing a bit more tricky.
            // TODO; this will eat threads; any other way to do it?

            Uninterruptibles.sleepUninterruptibly(5,TimeUnit.SECONDS);
        }
        else {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByObjectId(context, userOidOptional.get());
            authenticateUserResult.token = authenticationService.generateToken(user);
        }

        return authenticateUserResult;
    }

    @Override
    public RenewTokenResult renewToken(RenewTokenRequest renewTokenRequest) {
        Preconditions.checkNotNull(renewTokenRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(renewTokenRequest.token));
        RenewTokenResult result = new RenewTokenResult();

        Optional<ObjectId> userOidOptional = authenticationService.authenticateByToken(renewTokenRequest.token);

        if(userOidOptional.isPresent()) {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByObjectId(context, userOidOptional.get());
            result.token = authenticationService.generateToken(user);
            LOGGER.debug("did renew token for user; {}", user.toString());
        }
        else {
            LOGGER.info("unable to renew token");
        }

        return result;
    }


    @Override
    public ChangePasswordResult changePassword(
            ChangePasswordRequest changePasswordRequest)
            throws ObjectNotFoundException, AuthorizationFailureException, ValidationException {

        Preconditions.checkNotNull(changePasswordRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.newPasswordClear));

        // get the logged in and target user.

        final ObjectContext context = serverRuntime.newContext();

        User authUser = obtainAuthenticatedUser(context);

        User targetUser = User.tryGetByNickname(context, changePasswordRequest.nickname).orElseThrow(
                () -> new ObjectNotFoundException(User.class.getSimpleName(), changePasswordRequest.nickname)
        );

        if(!authorizationService.check(context, authUser, targetUser, Permission.USER_CHANGEPASSWORD)) {
            LOGGER.info("the logged in user {} is not allowed to change the password of another user {}", authUser.getNickname(), changePasswordRequest.nickname);
            throw new AuthorizationFailureException();
        }

        // if the user is changing their own password then they need to know their existing password
        // first.

        if(!authenticationService.validatePassword(changePasswordRequest.newPasswordClear)) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        // we need to make sure that the captcha is valid.

        if(Strings.isNullOrEmpty(changePasswordRequest.captchaToken)) {
            throw new IllegalStateException("the captcha token must be supplied to change the password");
        }

        if(Strings.isNullOrEmpty(changePasswordRequest.captchaResponse)) {
            throw new IllegalStateException("the captcha response must be supplied to change the password");
        }

        if(!captchaService.verify(changePasswordRequest.captchaToken, changePasswordRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        // we need to make sure that the old and new passwords match-up.

        if(targetUser.getNickname().equals(authUser.getNickname())) {
            if (Strings.isNullOrEmpty(changePasswordRequest.oldPasswordClear)) {
                throw new IllegalStateException("the old password clear is required to change the password of a user unless the logged in user is root.");
            }

            if (!authenticationService.authenticateByNicknameAndPassword(
                    changePasswordRequest.nickname,
                    changePasswordRequest.oldPasswordClear).isPresent()) {

                // if the old password does not match to the user then we should present this
                // as a validation failure rather than an authorization failure.

                LOGGER.info("the supplied old password is invalid for the user {}", changePasswordRequest.nickname);

                throw new ValidationException(new ValidationFailure("oldPasswordClear", "mismatched"));
            }
        }

        targetUser.setPasswordHash(authenticationService.hashPassword(targetUser, changePasswordRequest.newPasswordClear));
        context.commitChanges();
        LOGGER.info("did change password for user {}", changePasswordRequest.nickname);

        return new ChangePasswordResult();
    }

    @Override
    public SearchUsersResult searchUsers(SearchUsersRequest searchUsersRequest) {
        Preconditions.checkNotNull(searchUsersRequest);

        final ObjectContext context = serverRuntime.newContext();

        if(!authorizationService.check(
                context,
                tryObtainAuthenticatedUser(context).orElse(null),
                null,
                Permission.USER_LIST)) {
            throw new AuthorizationFailureException();
        }

        UserSearchSpecification specification = new UserSearchSpecification();
        String exp = searchUsersRequest.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!= searchUsersRequest.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(searchUsersRequest.expressionType.name()));
        }

        specification.setLimit(searchUsersRequest.limit);
        specification.setOffset(searchUsersRequest.offset);
        specification.setIncludeInactive(null!= searchUsersRequest.includeInactive && searchUsersRequest.includeInactive);

        SearchUsersResult result = new SearchUsersResult();

        result.total = userService.total(context,specification);
        result.items = Collections.emptyList();

        if(0 != result.total) {
            List<User> searchedUsers = userService.search(context,specification);

            result.items = searchedUsers.stream().map(u -> {
                SearchUsersResult.User resultUser = new SearchUsersResult.User();
                resultUser.active = u.getActive();
                resultUser.nickname = u.getNickname();
                return resultUser;
            }).collect(Collectors.toList());
        }

        return result;

    }

    @Override
    public InitiatePasswordResetResult initiatePasswordReset(InitiatePasswordResetRequest initiatePasswordResetRequest) {
        Preconditions.checkNotNull(initiatePasswordResetRequest);

        if(!captchaService.verify(initiatePasswordResetRequest.captchaToken, initiatePasswordResetRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        try {
            passwordResetService.initiate(initiatePasswordResetRequest.email);
        }
        catch(Throwable th) {
            LOGGER.error("unable to initiate password reset", th);
        }

        return new InitiatePasswordResetResult();
    }

    @Override
    public CompletePasswordResetResult completePasswordReset(CompletePasswordResetRequest completePasswordResetRequest) {
        Preconditions.checkNotNull(completePasswordResetRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.captchaToken), "a capture token is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.token), "a token is required to facilitate the reset of the password");
        Preconditions.checkState(!Strings.isNullOrEmpty(completePasswordResetRequest.passwordClear), "a new password is required to reset the password");

        if(!captchaService.verify(completePasswordResetRequest.captchaToken, completePasswordResetRequest.captchaResponse)) {
            throw new CaptchaBadResponseException();
        }

        try {
            passwordResetService.complete(
                    completePasswordResetRequest.token,
                    completePasswordResetRequest.passwordClear);
        }
        catch(Throwable th) {
            LOGGER.error("unable to complete password reset", th);
        }

        return new CompletePasswordResetResult();
    }

}
