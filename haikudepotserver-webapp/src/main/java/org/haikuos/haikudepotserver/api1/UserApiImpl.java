/*
 * Copyright 2013-2014 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.*;
import org.haikuos.haikudepotserver.captcha.CaptchaService;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class UserApiImpl extends AbstractApiImpl implements UserApi {

    protected static Logger logger = LoggerFactory.getLogger(UserApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    CaptchaService captchaService;

    @Resource
    AuthenticationService authenticationService;

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) {

        Preconditions.checkNotNull(createUserRequest);
        Preconditions.checkNotNull(!Strings.isNullOrEmpty(createUserRequest.nickname));
        Preconditions.checkNotNull(!Strings.isNullOrEmpty(createUserRequest.passwordClear));
        Preconditions.checkNotNull(!Strings.isNullOrEmpty(createUserRequest.captchaToken));
        Preconditions.checkNotNull(!Strings.isNullOrEmpty(createUserRequest.captchaResponse));

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
            throw new org.haikuos.haikudepotserver.api1.support.ValidationException(
                    new org.haikuos.haikudepotserver.api1.support.ValidationFailure(
                            User.NICKNAME_PROPERTY,"required")
            );
        }

        final ObjectContext context = serverRuntime.getContext();

        //need to check that the nickname is not already in use.

        if(User.getByNickname(context,createUserRequest.nickname).isPresent()) {
            throw new org.haikuos.haikudepotserver.api1.support.ValidationException(
                    new org.haikuos.haikudepotserver.api1.support.ValidationFailure(
                            User.NICKNAME_PROPERTY,"notunique")
            );
        }

        User user = context.newObject(User.class);
        user.setNickname(createUserRequest.nickname);
        user.setPasswordSalt(); // random
        user.setPasswordHash(authenticationService.hashPassword(user, createUserRequest.passwordClear));
        context.commitChanges();

        logger.info("data create user; {}",user.getNickname());

        return new CreateUserResult();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getUserRequest.nickname));

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);

        if(!authUser.getNickname().equals(getUserRequest.nickname) && !authUser.getDerivedCanManageUsers()) {
            throw new AuthorizationFailureException();
        }

        Optional<User> user = User.getByNickname(context, getUserRequest.nickname);

        if(!user.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME_PROPERTY);
        }

        GetUserResult result = new GetUserResult();
        result.nickname = user.get().getNickname();
        result.isRoot = user.get().getIsRoot();
        return result;
    }

    // TODO; some sort of brute-force checking here; too many authentication requests in a short period; go into lock-down?

    @Override
    public AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest) {
        Preconditions.checkNotNull(authenticateUserRequest);
        AuthenticateUserResult authenticateUserResult = new AuthenticateUserResult();
        authenticateUserResult.authenticated = false;

        if(null!=authenticateUserRequest.nickname) {
            authenticateUserRequest.nickname = authenticateUserRequest.nickname.trim();
        }

        if(null!=authenticateUserRequest.passwordClear) {
            authenticateUserRequest.passwordClear = authenticateUserRequest.passwordClear.trim();
        }

        authenticateUserResult.authenticated = authenticationService.authenticate(
                authenticateUserRequest.nickname,
                authenticateUserRequest.passwordClear).isPresent();

        // if the authentication has failed then best to sleep for a moment
        // to make brute forcing a bit more tricky.

        if(!authenticateUserResult.authenticated) {
            Uninterruptibles.sleepUninterruptibly(5,TimeUnit.SECONDS);
        }

        return authenticateUserResult;
    }

    @Override
    public ChangePasswordResult changePassword(
            ChangePasswordRequest changePasswordRequest)
            throws ObjectNotFoundException, AuthorizationFailureException, ValidationException {

        Preconditions.checkNotNull(changePasswordRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(changePasswordRequest.newPasswordClear));

        if(!authenticationService.validatePassword(changePasswordRequest.newPasswordClear)) {
            throw new ValidationException(new ValidationFailure("passwordClear", "invalid"));
        }

        final ObjectContext context = serverRuntime.getContext();

        User authUser = obtainAuthenticatedUser(context);

        // if the logged in user is non-root then we need to make sure that the captcha
        // is valid.

        if(!authUser.getIsRoot()) {

            if(Strings.isNullOrEmpty(changePasswordRequest.captchaToken)) {
                throw new IllegalStateException("the captcha token must be supplied to change the password");
            }

            if(Strings.isNullOrEmpty(changePasswordRequest.captchaResponse)) {
                throw new IllegalStateException("the captcha response must be supplied to change the password");
            }

            if(!captchaService.verify(changePasswordRequest.captchaToken, changePasswordRequest.captchaResponse)) {
                throw new CaptchaBadResponseException();
            }
        }

        // if the logged in user is not root then only the user who has authenticated can change their password.

        if(!authUser.getNickname().equals(changePasswordRequest.nickname)) {
            if(authUser.getIsRoot()) {
                logger.info("allowing change password for root user");
            }
            else {
                logger.info("the logged in user {} is not allowed to change the password of another user {}",authUser.getNickname(),changePasswordRequest.nickname);
                throw new AuthorizationFailureException();
            }
        }

        // if the logged in user is non-root then we need to make sure that the old and new passwords
        // match-up.

        if(!authUser.getIsRoot()) {

            if(Strings.isNullOrEmpty(changePasswordRequest.oldPasswordClear)) {
                throw new IllegalStateException("the old password clear is required to change the password of a user unless the logged in user is root.");
            }

            if(!authenticationService.authenticate(
                    changePasswordRequest.nickname,
                    changePasswordRequest.oldPasswordClear).isPresent()) {

                // if the old password does not match to the user then we should present this
                // as a validation failure rather than an authorization failure.

                logger.info("the supplied old password is invalid for the user {}", changePasswordRequest.nickname);

                throw new ValidationException(new ValidationFailure("oldPasswordClear","mismatched"));
            }
        }

        Optional<User> userOptional = User.getByNickname(context, changePasswordRequest.nickname);

        if(!userOptional.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), changePasswordRequest.nickname);
        }

        User user = userOptional.get();
        user.setPasswordHash(authenticationService.hashPassword(user, changePasswordRequest.newPasswordClear));
        context.commitChanges();
        logger.info("did change password for user {}", changePasswordRequest.nickname);

        return new ChangePasswordResult();
    }


}
