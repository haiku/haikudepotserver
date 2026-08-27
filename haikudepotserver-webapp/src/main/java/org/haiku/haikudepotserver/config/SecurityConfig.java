/*
 * Copyright 2020-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.multipage.controller.LoginController;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.*;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.haiku.haikudepotserver.user.model.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <p>This will configure the security filters that are used by Spring Security.</p>
 */

@Configuration
public class SecurityConfig {

    private final ServerRuntime serverRuntime;

    private final UserAuthenticationService userAuthenticationService;

    private final RepositoryService repositoryService;

    private final UserService userService;

    private final ObjectMapper objectMapper;

    public SecurityConfig(
            ServerRuntime serverRuntime,
            UserAuthenticationService userAuthenticationService,
            RepositoryService repositoryService,
            UserService userService,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.serverRuntime = serverRuntime;
        this.userAuthenticationService = userAuthenticationService;
        this.repositoryService = repositoryService;
        this.userService = userService;
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
        http.logout(logout ->
                logout.logoutUrl("/%s/%s".formatted(
                                WebConstants.SEGMENT_SECURITY, WebConstants.SEGMENT_LOGOUT
                        ))
                        .logoutSuccessHandler(new LogoutSuccessHandler())
        );

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
        http.addFilterBefore(
                new BearerTokenAuthenticationFilter(userAuthenticationService),
                BasicAuthenticationFilter.class);

        // This sets up a login page.
        http
                .authenticationProvider(new UserAuthenticationProvider(userAuthenticationService))
                .formLogin(
                        form -> form
                                .loginPage("/%s/%s".formatted(
                                        WebConstants.SEGMENT_SECURITY,
                                        WebConstants.SEGMENT_LOGIN))
                                .loginProcessingUrl("/%s/%s".formatted(
                                        WebConstants.SEGMENT_SECURITY,
                                        WebConstants.SEGMENT_LOGIN_PROCESSING))
                                .successHandler(new AuthenticationSuccessHandler(serverRuntime, userService))
                                .failureHandler(new AuthenticationFailureHandler(
                                        UriComponentsBuilder.fromUriString("/%s/%s?%s".formatted(
                                                WebConstants.SEGMENT_SECURITY,
                                                WebConstants.SEGMENT_LOGIN,
                                                LoginController.KEY_ERROR
                                        )).build()))
                                .permitAll() // OK for now because everything is authorized in code
                );

        // checks are done in code logic so allow everything through.

        http.authorizeHttpRequests(ar -> ar.anyRequest().permitAll());

        return http.build();
    }

}
