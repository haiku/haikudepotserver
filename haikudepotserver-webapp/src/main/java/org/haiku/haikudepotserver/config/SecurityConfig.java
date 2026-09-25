/*
 * Copyright 2020-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.feed.model.FeedService;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.controller.LoginController;
import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes;
import org.haiku.haikudepotserver.pkg.controller.*;
import org.haiku.haikudepotserver.reference.controller.ReferenceController;
import org.haiku.haikudepotserver.repository.controller.RepositoryController;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.security.*;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.haiku.haikudepotserver.user.controller.UserController;
import org.haiku.haikudepotserver.user.model.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <p>This will configure the security filters that are used by Spring Security.</p>
 */

@Configuration
public class SecurityConfig {

    private final ServerRuntime serverRuntime;

    private final MultipageWebResourceService multipageWebResourceService;

    private final UserAuthenticationService userAuthenticationService;

    private final RepositoryService repositoryService;

    private final UserService userService;

    private final ObjectMapper objectMapper;

    public SecurityConfig(
            ServerRuntime serverRuntime,
            MultipageWebResourceService multipageWebResourceService,
            UserAuthenticationService userAuthenticationService,
            RepositoryService repositoryService,
            UserService userService,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.serverRuntime = serverRuntime;
        this.multipageWebResourceService = multipageWebResourceService;
        this.userAuthenticationService = userAuthenticationService;
        this.repositoryService = repositoryService;
        this.userService = userService;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new NoOpAuthenticationManager();
    }

    /**
     * <p>This endpoint is supporting triggering the import of a repository. It will sometimes
     * require username + password authentication.</p>
     */

    @Bean
    @Order(1)
    public SecurityFilterChain filterChainRepositoryImport(HttpSecurity http) {
        http
                .securityMatcher(new RepositoryController.ImportRequestMatcher())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(new RepositoryAuthenticationProvider(serverRuntime, repositoryService))
                .httpBasic(hb -> {
                    hb.authenticationEntryPoint(new AuthenticationEntryPoint(objectMapper));
                    hb.authenticationDetailsSource(new RepositoryAuthenticationDetailsSource());
                });

        http.csrf(AbstractHttpConfigurer::disable);

        // checks are done in code logic so allow everything through.
        http.authorizeHttpRequests(ar -> ar.anyRequest().permitAll());

        return http.build();
    }

    /**
     * <p>This is a security filter chain for stateless endpoints where no session should be in play.
     * By configuring this, it will reduce the frequency of retreiving the session from the session
     * store.</p>
     */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChainGeneralStateless(HttpSecurity http) {

        WebResourcePathPrefixes pathPrefixes = multipageWebResourceService.getPathPrefixes();

        http
                .securityMatcher(

                        // basic system
                        "/error",
                        "/_error",
                        "/favicon.*",

                        // feed
                        "/feed/**", // TODO (deprecated) be removed
                        "%s/**".formatted(FeedService.PATH_ROOT),
                        // pkg
                        "/%s/**".formatted(PkgController.SEGMENT_PKG),
                        "/%s/**".formatted(PkgDownloadController.SEGMENT_PKGDOWNLOAD),
                        "/%s/**".formatted(PkgIconController.SEGMENT_PKGICON),
                        "/%s".formatted(PkgIconController.SEGMENT_GENERICPKGICON),
                        "/%s/**".formatted(PkgScreenshotController.SEGMENT_SCREENSHOT),
                        "/%s/**".formatted(PkgScreenshotController.SEGMENT_SCREENSHOT_LEGACY), // TODO (deprecated) be removed
                        "/%s/**".formatted(PkgSearchController.SEGMENT_SEARCH),
                        "/%s/**".formatted(PkgSearchController.SEGMENT_SEARCH_LEGACY), // TODO (deprecated) be removed
                        // reference
                        "/%s/**".formatted(ReferenceController.SEGMENT_REFERENCE),
                        // repository
                        // Rules from earlier security filter chains will catch the import case.
                        "/%s/**".formatted(RepositoryController.SEGMENT_REPOSITORY),
                        // user
                        "/%s/%s/**".formatted(UserController.SEGMENT_USER, UserController.SEGMENT_USAGE_CONDITIONS),

                        // multipage support
                        "%s**".formatted(pathPrefixes.img()),
                        "%s**".formatted(pathPrefixes.js()),
                        "%s**".formatted(pathPrefixes.css()),
                        // SPA support
                        // TODO (andponlin) remove.
                        "/%s/**".formatted(WebConstants.SEGMENT_JS),
                        "/%s/**".formatted(WebConstants.SEGMENT_CSS),
                        "/%s/**".formatted(WebConstants.SEGMENT_IMG),
                        "/__log/**"
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        http.csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(ar -> ar.anyRequest().permitAll());

        return http.build();
    }

    /**
     * <p>This is a security filter chain for stateful endpoints where somebody <em>might</em> be logged in.</p>
     */
    @Bean
    @Order(3)
    public SecurityFilterChain filterChain(HttpSecurity http) {
        AuthenticationEntryPoint authenticationEntryPoint = new AuthenticationEntryPoint(objectMapper);
        AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandler(objectMapper);

        http.securityMatcher(
                "/", // TODO (andponlin) remove SPA "launch page" once SSO
                "/%s".formatted(MultipageConstants.SEGMENT_MULTIPAGE),
                "/%s/**".formatted(MultipageConstants.SEGMENT_MULTIPAGE),
                "/%s/**".formatted(WebConstants.SEGMENT_SECURITY),
                "/__api/**", // TODO (andponlin) use constants
                "/api/**", // TODO (andponlin) remove; legacy
                "/%s/**".formatted(WebConstants.PATH_COMPONENT_SECURED) // TODO (andponlin) look into this path format
        );

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

// TODO (andponlin) re-enable this once SSO is configured.

//        http.oauth2Login((c) -> {
//                    c.authorizationEndpoint(e -> e.baseUri(
//                            "/%s/oauth2/authorization".formatted(WebConstants.SEGMENT_SECURITY)
//                    ));
//                    c.redirectionEndpoint(e -> e.baseUri(
//                            "/%s/login/oauth2/code/*".formatted(WebConstants.SEGMENT_SECURITY)
//                    ));
//                    c.successHandler(new AuthenticationSuccessHandler(serverRuntime, userService));
//                });

        // basic authentication; note that this covers both the regular user authentication
        // and the special authentication case for the repository security.
        http
                .authenticationProvider(new UserAuthenticationProvider(userAuthenticationService))
                .httpBasic(hb -> {
                    hb.authenticationEntryPoint(authenticationEntryPoint);
                });

        // this covers authentication by supplying a JWT bearer token as well as an occasional need
        // for the JWT to be supplied as a query parameter (needs to be phased out).
        http.addFilterBefore(
                new BearerTokenAuthenticationFilter(userAuthenticationService),
                BasicAuthenticationFilter.class);

        // TODO (andponlin) remove once SSO project is complete
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

    /**
     * <p>This last security chain will deny anything else.</p>
     */
    @Bean
    @Order(3)
    public SecurityFilterChain filterChainLastResort(HttpSecurity http) {
        http.authorizeHttpRequests(ar -> ar.anyRequest().denyAll());
        return http.build();
    }

}
