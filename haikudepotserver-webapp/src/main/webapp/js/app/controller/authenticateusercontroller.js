/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AuthenticateUserController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','userState','errorHandling',
        function(
            $scope,$log,$location,
            jsonRpc,constants,userState,errorHandling) {

            if(userState.user()) {
                throw 'it is not possible to enter the authenticate user controller with a currently authenticated user';
            }

            $scope.didFailAuthentication = false;
            $scope.amAuthenticating = false;
            $scope.didCreate = !!$location.search()['didCreate'];
            $scope.didChangePassword = !!$location.search()['didChangePassword'];
            $scope.authenticationDetails = {
                nickname : undefined,
                passwordClear : undefined
            };
            $scope.breadcrumbItems = [{
                title : 'Login',
                path : $location.path()
            }];

            if($location.search()['nickname']) {
                $scope.authenticationDetails.nickname = $location.search()['nickname'];
            }

            $scope.shouldSpin = function() {
                return $scope.amAuthenticating;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.authenticateUserForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // This function will take the data from the form and will authenticate
            // the user from this data.

            $scope.goAuthenticate = function() {

                if($scope.authenticateUserForm.$invalid) {
                    throw 'expected the authentication of a user only to be possible if the form is valid';
                }

                $scope.didFailAuthentication = false;
                $scope.amAuthenticating = true;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_USER,
                        "authenticateUser",
                        [{
                            nickname : $scope.authenticationDetails.nickname,
                            passwordClear : $scope.authenticationDetails.passwordClear
                        }]
                    ).then(
                    function(result) {
                        if(result.authenticated) {

                            userState.user({
                                nickname : $scope.authenticationDetails.nickname,
                                passwordClear : $scope.authenticationDetails.passwordClear
                            });

                            $log.info('successful authentication; '+$scope.authenticationDetails.nickname);

                            // now pull back to the server to incorporate other data about the user.  This is necessary
                            // here in order to obtain the natural language of the user.  Note that owing to the async
                            // communication going on here, the change of the natural language may happen some time
                            // after the user has actually changed.

                            jsonRpc.call(
                                constants.ENDPOINT_API_V1_USER,
                                "getUser",
                                [ {
                                    nickname : $scope.authenticationDetails.nickname
                                }]).then(
                                function(userData) {

                                    userState.naturalLanguageCode(userData.naturalLanguageCode);

                                    // Finally take the user somewhere post-login.
                                    // Either the user specified where they want to return to
                                    // of we just take them back to their home page.

                                    var destination = $location.search()['destination'];

                                    if(destination && 0!=destination.length) {
                                        var s = angular.copy($location.search());
                                        delete s['destination'];
                                        $location.path(destination).search(s);
                                    }
                                    else {
                                        $location.path('/').search({});
                                    }

                                },
                                function(e) {
                                    $log.error('unable to get the natural language of the newly authenticated user');
                                    errorHandling.handleJsonRpcError(e);
                                }
                            );
                        }
                        else {
                            $log.info('failed authentication; '+$scope.authenticationDetails.nickname);
                            $scope.didFailAuthentication = true;
                            $scope.authenticationDetails.passwordClear = undefined;
                        }

                        $scope.amAuthenticating = false;
                    },
                    function(err) {
                        $scope.amAuthenticating = false;
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

        }
    ]
);