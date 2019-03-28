/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AuthenticateUserController',
    [
        '$scope', '$log', '$location',
        'jsonRpc', 'constants', 'userState', 'errorHandling', 'breadcrumbs',
        'breadcrumbFactory', 'jwt',
        function(
            $scope, $log, $location,
            jsonRpc, constants, userState, errorHandling, breadcrumbs,
            breadcrumbFactory, jwt) {

            if (userState.user()) {
                throw Error('it is not possible to enter the authenticate user controller with a currently authenticated user');
            }

            var STATUS_FAILURE = 'FAILURE';
            var STATUS_SUCCESS = 'SUCCESS';
            var STATUS_AGREE_USER_CONDITIONS = 'AGREE_USER_CONDITIONS';

            // used to agreeing to updated user usage conditions.
            $scope.userUsageConditions = undefined;
            $scope.amAgreeingtoUserUsageConditions = false;
            $scope.tokenPendingAgreeUserUsageConditions = undefined;
            $scope.agreeUserUsageConditions = {};

            // used for authentication purposes.
            $scope.didFailAuthentication = false;
            $scope.amAuthenticating = false;
            $scope.didCreate = !!$location.search()['didCreate'];
            $scope.didChangePassword = !!$location.search()['didChangePassword'];
            $scope.authenticationDetails = {
                nickname : undefined,
                passwordClear : undefined
            };

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAuthenticate())
            ]);

            if ($location.search()['nickname']) {
                $scope.authenticationDetails.nickname = $location.search()['nickname'];
            }

            $scope.shouldSpin = function() {
                return $scope.amAuthenticating || $scope.amAgreeingtoUserUsageConditions;
            };

            $scope.deriveAuthenticateUserFormControlsContainerClasses = function(name) {
                return $scope.authenticateUserForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function toAuthorizationHeader(token) {
                return { 'Authorization': 'Bearer ' + token };
            }

            /**
             * Fetch the user's natural language and add it into the data structure that is
             * provided.
             */

            function fetchUserNaturalLanguageCode(result) {

                if (!result) {
                    return;
                }

                if (!result.token) {
                    throw Error('expected that a token would be available to '
                        + 'get the natural language.');
                }

                // now pull back to the server to incorporate other data about the user.  This is necessary
                // here in order to obtain the natural language of the user.  Note that owing to the async
                // communication going on here, the change of the natural language may happen some time
                // after the user has actually changed.

                return jsonRpc.callWithAdditionalHeaders(
                    constants.ENDPOINT_API_V1_USER,
                    "getUser",
                    [{ nickname : result.nickname }],
                    null,
                    toAuthorizationHeader(result.token)
                ).then(
                    function (userData) {
                        return _.extend(_.clone(result), { naturalLanguageCode: userData.naturalLanguageCode });
                    },
                    function (e) {
                        $log.error('unable to get the natural language of the newly authenticated user');
                        errorHandling.handleJsonRpcError(e);
                    }
                );
            }

            /**
             * <p>Returns an object which contains a status.  The status
             * indicates the result of the authentication.  Other data may be
             * included in the result as well.  This function will return a
             * promise that resolves to the final result of the authentication
             * process.</p>
             */

            function authenticate(nickname, passwordClear) {

                function fetchUserUsageConditions(result) {
                    return jsonRpc.call(constants.ENDPOINT_API_V1_USER, "getUserUsageConditions", [{}])
                    .then(
                        function(userUsageConditionsData) {
                            return _.extend(_.clone(result), { userUsageConditions: userUsageConditionsData });
                        },
                        errorHandling.handleJsonRpcError
                    );
                }

                return jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "authenticateUser",
                    [{
                        nickname : nickname,
                        passwordClear : passwordClear
                    }]
                ).then(
                    // convert the response into a status to say what needs to
                    // happen next.
                    function (result) {

                        function toStatus(token) {
                            if (!token || !token.length) {
                                return STATUS_FAILURE;
                            }

                            if (jwt.requiresAgreeUserUsageConditions(token)) {
                                return STATUS_AGREE_USER_CONDITIONS;
                            }

                            return STATUS_SUCCESS;
                        }

                        function toNickname(token) {
                            if (token && token.length) {
                                return jwt.tokenNickname(token);
                            }

                            return undefined;
                        }

                        if (!result) {
                            return;
                        }

                        return {
                            status: toStatus(result.token),
                            nickname: toNickname(result.token),
                            token: result.token
                        };
                    },
                    function (err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                ).then(
                    function (result) {
                        if (!result) {
                            return;
                        }

                        switch (result.status) {
                            case STATUS_SUCCESS:
                                return fetchUserNaturalLanguageCode(result);
                            case STATUS_AGREE_USER_CONDITIONS:
                                return fetchUserUsageConditions(result);
                            default: // failure
                                return result;
                        }
                    }
                );
            }

            /**
             * <p>Authenticates the user and manages the user interface to
             * reflect results such as authentication failure, success or other
             * situations.</p>
             */

            $scope.goAuthenticate = function() {

                if ($scope.amAuthenticating) {
                    $log.info('attempt to authenticate concurrently');
                    return;
                }

                if ($scope.authenticateUserForm.$invalid) {
                    throw Error('expected the authentication of a user only to be possible if the form is valid');
                }

                $scope.didFailAuthentication = false;
                $scope.amAuthenticating = true;
                var nickname = $scope.authenticationDetails.nickname;
                var passwordClear = $scope.authenticationDetails.passwordClear;
                $scope.authenticationDetails.passwordClear = undefined;

                authenticate(nickname, passwordClear)
                    .then(
                        function (result) {
                            if (!result) {
                                return;
                            }

                            switch (result.status) {
                                case STATUS_SUCCESS:
                                    userState.token(result.token);
                                    $log.info('did authenticate user [' + nickname + ']');
                                    userState.naturalLanguageCode(result.naturalLanguageCode);
                                    breadcrumbs.popAndNavigate();
                                    break;
                                case STATUS_AGREE_USER_CONDITIONS:
                                    $scope.userUsageConditions = result.userUsageConditions;
                                    $log.info('authenticated user should agree to user usage conditions');
                                    $scope.tokenPendingAgreeUserUsageConditions = result.token;
                                    break;
                                default: // failure
                                    $log.info('failed authentication');
                                    $scope.didFailAuthentication = true;
                                    break;
                            }
                        }
                    ).finally(
                    function() {
                        $scope.amAuthenticating = false;
                    }
                );
            };

            $scope.canAgreeUserUsageConditions = function() {
                return !$scope.agreeUserUsageConditionsForm.$invalid &&
                    $scope.agreeUserUsageConditions.userUsageConditionsDocumentAgreed &&
                    $scope.agreeUserUsageConditions.userUsageConditionsIsMinimumAgeExceeded;
            };

            /**
             * <p>When the user has authenticated, they may be required to also
             * agree to updated user usage conditions.  In this case, they will
             * be presented with a new form.  The new form will require them to
             * agree and then the execution flow ends up here.</p>
             *
             * <p>Communications back to the application server uses the
             * temporary token that has been issued previously.</p>
             */

            $scope.goAgreeUserUsageConditions = function() {

                if ($scope.amAgreeingtoUserUsageConditions) {
                    $log.info('attempt to agree to user usage conditions concurrently');
                    return;
                }

                if ($scope.agreeUserUsageConditionsForm.$invalid) {
                    throw Error('agreement of a user usage conditions only to be possible if the form is valid');
                }

                $scope.amAgreeingtoUserUsageConditions = true;
                var nickname = jwt.tokenNickname($scope.tokenPendingAgreeUserUsageConditions);

                // make a special call that passes the authentication that was
                // provided earlier.  This is only suitable for agreeing to the
                // user usage conditions.

                return jsonRpc.callWithAdditionalHeaders(
                    constants.ENDPOINT_API_V1_USER,
                    "agreeUserUsageConditions",
                    [{
                        nickname : nickname,
                        userUsageConditionsCode: $scope.userUsageConditions.code
                    }],
                    null, // id; not important
                    toAuthorizationHeader($scope.tokenPendingAgreeUserUsageConditions)
                ).then(
                    function () {
                        return {
                            status: STATUS_SUCCESS,
                            nickname: nickname,
                            token: $scope.tokenPendingAgreeUserUsageConditions
                        };
                    },
                    function (e) {
                        $log.error('unable for the user [' + nickname +
                            '] to agree to user usage conditions');
                        errorHandling.handleJsonRpcError(e);
                    }
                ).then(
                    // the old token is restricted in that contains flags that
                    // signal to the operating system that the user needs to
                    // agree to user usage conditions.  Now swap this token with
                    // another one that now will no longer have this restriction.

                    function (result) {
                        if (!result) {
                            return;
                        }
                        return jsonRpc.call(
                            constants.ENDPOINT_API_V1_USER,
                            'renewToken',
                            [ { token: result.token } ]
                        ).then(
                            function (renewTokenResponse) {
                                if (renewTokenResponse.token) {
                                    return _.extend(_.clone(result), { token: renewTokenResponse.token })
                                }
                                else {
                                    $log.info('was not able to renew authentication token');
                                    errorHandling.navigateToError(jsonRpc.errorCodes.AUTHORIZATIONFAILURE); // simulates this happening
                                }
                            },
                            function (err) {
                                $log.info('failure to renew the authentication token');
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    }
                ).then(
                    // now capture the user's natural language.
                    fetchUserNaturalLanguageCode
                ).then(
                    function (result) {
                        if (!result) {
                            return;
                        }
                        userState.token(result.token);
                        $log.info('did authenticate user [' + nickname + ']');
                        userState.naturalLanguageCode(result.naturalLanguageCode);
                        breadcrumbs.popAndNavigate();
                    }
                ).finally(
                    function() {
                        $scope.amAgreeingtoUserUsageConditions = false;
                    }
                )
            };

            /**
             * <p>This is hit from a link at the bottom of the page where,
             * instead of authenticating the user has the option to instead
             * create a new user.</p>
             */

            $scope.goCreateUser = function() {
                breadcrumbs.resetAndNavigate([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createAddUser()
                ]);
            };

        }
    ]
);