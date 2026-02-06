/*
 * Copyright 2020-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.*;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.CompositeFilter;

import java.util.List;

/**
 * <p>This will setup the security filters that are used by Spring Security.</p>
 */

@Configuration
public class SecurityConfig {

    private final ServerRuntime serverRuntime;

    private final UserAuthenticationService userAuthenticationService;

    private final RepositoryService repositoryService;

    private final ObjectMapper objectMapper;

    public SecurityConfig(
            ServerRuntime serverRuntime,
            UserAuthenticationService userAuthenticationService,
            RepositoryService repositoryService,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.serverRuntime = serverRuntime;
        this.userAuthenticationService = userAuthenticationService;
        this.repositoryService = repositoryService;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new NoOpAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        AuthenticationEntryPoint authenticationEntryPoint = new AuthenticationEntryPoint(objectMapper);
        AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandler(objectMapper);

        http.exceptionHandling(eh -> {
            eh.accessDeniedHandler(accessDeniedHandler);
            eh.authenticationEntryPoint(authenticationEntryPoint);
        });

        http.csrf(AbstractHttpConfigurer::disable);
        http.headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // basic authentication; note that this covers both the regular user authentication
        // and the special authentication case for the repository security.

        http
                .authenticationProvider(new UserAuthenticationProvider(userAuthenticationService))
                .authenticationProvider(new RepositoryAuthenticationProvider(serverRuntime, repositoryService))
                .httpBasic(hb -> {
                    hb.authenticationEntryPoint(authenticationEntryPoint);
                    hb.authenticationDetailsSource(new RepositoryAuthenticationDetailsSource());
                });

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

        http.authorizeHttpRequests(ar -> ar.anyRequest().permitAll());

        return http.build();
    }

}
