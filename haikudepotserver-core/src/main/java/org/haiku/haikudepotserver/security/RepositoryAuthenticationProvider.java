/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;


import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.haiku.haikudepotserver.security.model.RepositoryAuthenticationDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

/**
 * <p>This {@link org.springframework.security.authentication.AuthenticationProvider} is for
 * authenticating requests that are trying to perform operations on a repository.</p>
 */

public class RepositoryAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

    private final ServerRuntime serverRuntime;

    private final AuthenticationService authenticationService;

    public RepositoryAuthenticationProvider(
            ServerRuntime serverRuntime,
            AuthenticationService authenticationService) {
        this.serverRuntime = serverRuntime;
        this.authenticationService = authenticationService;
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
        return Optional.of(username)
                .flatMap(rc -> Repository.tryGetByCode(context, username))
                .filter(r -> StringUtils.isNotBlank(r.getPasswordHash()))
                .filter(r -> StringUtils.isNotBlank(r.getPasswordSalt()))
                .filter(r -> r.getPasswordHash().equals(authenticationService.hashPassword(r.getPasswordSalt(), password)))
                .map(r -> new RepositoryAuthentication(r.getCode()));
    }
}
