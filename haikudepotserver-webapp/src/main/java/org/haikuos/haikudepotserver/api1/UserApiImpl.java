/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.CaptchaBadResponseException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.captcha.CaptchaService;
import org.haikuos.haikudepotserver.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class UserApiImpl implements UserApi {

    protected static Logger logger = LoggerFactory.getLogger(UserApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    CaptchaService captchaService;

    @Resource
    AuthenticationManager authenticationManager;

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) {

        Preconditions.checkNotNull(createUserRequest);
        Preconditions.checkNotNull(createUserRequest.captchaToken);
        Preconditions.checkNotNull(createUserRequest.captchaResponse);

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
        user.setPasswordHash(Hashing.sha256().hashUnencodedChars(user.getPasswordSalt() + createUserRequest.passwordClear).toString());
        context.commitChanges();

        logger.info("data create user; {}",user.getNickname());

        return new CreateUserResult();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getUserRequest.nickname));

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = User.getByNickname(context, getUserRequest.nickname);

        if(!user.isPresent()) {
            throw new ObjectNotFoundException(User.class.getSimpleName(), User.NICKNAME_PROPERTY);
        }

        GetUserResult result = new GetUserResult();
        result.nickname = user.get().getNickname();
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

        // Use the spring security system to authenticate in the same fashion as the regular basic auth
        // of the HTTP requests.

        UsernamePasswordAuthenticationToken authRequest =
                new UsernamePasswordAuthenticationToken(
                        authenticateUserRequest.nickname,
                        authenticateUserRequest.passwordClear);

        try {
            authenticateUserResult.authenticated = authenticationManager.authenticate(authRequest).isAuthenticated();
        }
        catch(AuthenticationException ae) {
            // ignore.
        }

        // if the authentication has failed then best to sleep for a moment
        // to make brute forcing a bit more tricky.

        if(!authenticateUserResult.authenticated) {
            Uninterruptibles.sleepUninterruptibly(5,TimeUnit.SECONDS);
        }

        return authenticateUserResult;
    }


}
