/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is here to provide funtions for manipulating JWT services.</p>
 */

angular.module('haikudepotserver').factory('jwt',
    [
        // no injections
        function() {

            /**
             * <p>Returns the number of milliseconds until the expiration date of the supplied token from now.  If
             * the token has already expired then this function will return a negative value.</p>
             */

            function millisUntilExpirationForToken(token) {
                var nowMs = new Date().getTime();
                var expMs = tokenExpirationDate(token).getTime();
                return expMs - nowMs;
            }

            function tokenClaimSet(token) {
                if (!token || !token.length) {
                    throw Error('missing json web token');
                }

                var parts = token.split('.');

                if (3 !== parts.length) {
                    throw Error('json web token should contain three dot-separated parts');
                }

                return angular.fromJson(window.atob(parts[1]));
            }

            /**
             * <p>If the user should agree to the user usage conditions then the
             * JWT token will contain a special value to indicate this.</p>
             */

            function requiresAgreeUserUsageConditions(token) {
                var claimSet = tokenClaimSet(token);
                return claimSet &&
                    undefined !== claimSet.ucnd &&
                    (true === claimSet.ucnd || 'true' === claimSet.ucnd)
            }

            /**
             * <p>If a user is authenticated with the system then this will return a non-null value that
             * represents the date at which the authentication will expire.</p>
             */

            function tokenExpirationDate(token) {
                var claimSet = tokenClaimSet(token);

                if (!claimSet || !claimSet.exp || !angular.isNumber(claimSet.exp)) {
                    throw Error('malformed claim set; unable to get the \'exp\' data');
                }

                return new Date(claimSet.exp * 1000);
            }

            /**
             * <p>The token is a 'json web token' that contains a 'subject'.  In the case of this application,
             * the subject contains the nickname of the user.</p>
             */

            function tokenNickname(token) {
                var claimSet = tokenClaimSet(token);

                if (!claimSet || !claimSet.sub) {
                    throw Error('malformed claim set; unable to get the \'sub\' data');
                }

                var sub = '' + claimSet.sub;
                var suffixIndex = sub.indexOf('@hds');

                if (-1 === suffixIndex) {
                    throw Error('malformed nickname in token; missing suffix');
                }

                return sub.substring(0,suffixIndex);
            }

            return {
                millisUntilExpirationForToken: millisUntilExpirationForToken,
                tokenNickname: tokenNickname,
                requiresAgreeUserUsageConditions: requiresAgreeUserUsageConditions
            };
        }
    ]
);