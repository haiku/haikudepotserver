/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service helps to integrate with the login process on the server to create a web
 * session.</p>
 */

angular.module('haikudepotserver').factory('webSession',
    [
        '$log',
        'breadcrumbFactory',
        function($log, breadcrumbFactory) {

            function deriveBreadcrumbUri(breadcrumb) {
                return breadcrumbFactory.toFullPath(breadcrumb || breadcrumbFactory.createHome());
            }

            function deriveSecurityUri(segment, redirectUri) {
                const loc = window.location;
                const loginHrefBase = `${loc.protocol}//${loc.hostname}:${loc.port}/__security/${segment}`;
                return `${loginHrefBase}?redirect_uri=${encodeURIComponent(redirectUri)}`;
            }

            function navigateToSecurityEndpoint(segment, redirectUri) {
                const loginHref = deriveSecurityUri(segment, redirectUri);
                $log.info(`navigating to the ${segment} [${loginHref}]`);
                window.location.href = loginHref;
            }

            function navigateToLogin(breadcrumb) {
                navigateToSecurityEndpoint("login", deriveBreadcrumbUri(breadcrumb));
            }

            function navigateToLogout(breadcrumb) {
                navigateToSecurityEndpoint("logout", deriveBreadcrumbUri(breadcrumb));
            }

            /**
             * <p>Navigates to the logout which then redirects to login, and then back to the nominated
             * breadcrumb.</p>
             */
            function navigateToLogoutLogin(breadcrumb) {
                const breadcrumbUri = deriveBreadcrumbUri(breadcrumb)
                const loginUri = deriveSecurityUri("login", breadcrumbUri);
                navigateToSecurityEndpoint("logout", loginUri);
            }

            return {
                navigateToLogin: navigateToLogin,
                navigateToLogout: navigateToLogout,
                navigateToLogoutLogin: navigateToLogoutLogin
            };

        }
    ]
);