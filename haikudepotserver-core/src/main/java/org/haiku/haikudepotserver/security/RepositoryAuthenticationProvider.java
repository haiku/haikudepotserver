/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;


import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.security.model.RepositoryAuthenticationDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

/**
 * <p>This {@link org.springframework.security.authentication.AuthenticationProvider} is for
 * authenticating requests that are trying to perform operations on a repository.</p>
 */

public class RepositoryAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryAuthenticationProvider.class);

    private final ServerRuntime serverRuntime;

    // TODO; this should not be using the *USER* auth service.
    private final UserAuthenticationService userAuthenticationService;

    public RepositoryAuthenticationProvider(
            ServerRuntime serverRuntime,
            UserAuthenticationService userAuthenticationService) {
        this.serverRuntime = serverRuntime;
        this.userAuthenticationService = userAuthenticationService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        return tryAuthenticateUsernamePassword(
                tryGetRepositoryCodeFromPrincipal(authentication)
                        .orElse(tryGetRepositoryCodeFromDetails(authentication).orElse(null)),
                Optional.ofNullable(authentication.getCredentials())
                        .map(p -> StringUtils.trimToNull(p.toString()))
                        .orElse(null)
        ).orElse(null);
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return aClass == UsernamePasswordAuthenticationToken.class;
    }

    private Optional<String> tryGetRepositoryCodeFromPrincipal(Authentication authentication) {
        return Optional.ofNullable(authentication.getPrincipal())
                .map(Object::toString)
                .map(StringUtils::trimToNull);
    }

    private Optional<String> tryGetRepositoryCodeFromDetails(Authentication authentication) {
        return Optional.of(authentication.getDetails())
                .filter(d -> d instanceof RepositoryAuthenticationDetails)
                .map(d -> (RepositoryAuthenticationDetails) d)
                .map(RepositoryAuthenticationDetails::getRepositoryCode);
    }

    private Optional<Authentication> tryAuthenticateUsernamePassword(String username, String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Optional.empty();
        }

        ObjectContext context = serverRuntime.newContext();
        Repository repository = Optional.of(username)
                .flatMap(rc -> Repository.tryGetByCode(context, username))
                .orElse(null);

        if (null == repository) {
            String msg = "unable to find the repository [" + username + "]";
            LOGGER.info(msg);
            throw new UsernameNotFoundException(msg);
        }

        if (null == repository.getPasswordHash() || null == repository.getPasswordSalt()) {
            LOGGER.info("repository [{}] has no password hash / salt", repository);
            return Optional.empty();
        }

        String passwordHash = userAuthenticationService.hashPassword(repository.getPasswordSalt(), password);

        if (!repository.getPasswordHash().equals(passwordHash)) {
            throw new BadCredentialsException("authentication against repository [" + repository + "] failed");
        }

        RepositoryAuthentication result = new RepositoryAuthentication(repository.getCode());
        result.setAuthenticated(true);
        return Optional.of(result);
    }
}
