/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.*;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.CompositeFilter;

import java.util.List;

/**
 * <p>This will setup the security filters that are used by Spring Security.</p>
 */

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final ServerRuntime serverRuntime;

    private final UserAuthenticationService userAuthenticationService;

    private final RepositoryService repositoryService;

    public SecurityConfig(
            ServerRuntime serverRuntime,
            UserAuthenticationService userAuthenticationService,
            RepositoryService repositoryService) {
        this.serverRuntime = serverRuntime;
        this.userAuthenticationService = userAuthenticationService;
        this.repositoryService = repositoryService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // basic authentication; note that this covers both the regular user authentication
        // as well as a special authentication case for the repository security.

        http
                .authenticationProvider(new UserAuthenticationProvider(userAuthenticationService))
                .authenticationProvider(new RepositoryAuthenticationProvider(serverRuntime, repositoryService))
                .httpBasic().authenticationDetailsSource(new RepositoryAuthenticationDetailsSource());

        // this covers authentication by supplying a JWT bearer token as well as an occasional need
        // for the JWT to be supplied as a query parameter (needs to be phased out).

        CompositeFilter tokenFilters = new CompositeFilter();
        tokenFilters.setFilters(
                List.of(
                        new BearerTokenAuthenticationFilter(userAuthenticationService),
                        new QueryParamAuthenticationFilter(userAuthenticationService)
                )
        );
        http.addFilterBefore(tokenFilters, BasicAuthenticationFilter.class);

        // checks are done in code logic so allow everything through.

        http.authorizeRequests().anyRequest().permitAll();
    }

}
