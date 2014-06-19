/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller allows the user to update the details of a user within the system.</p>
 */

angular.module('haikudepotserver').controller(
    'EditUserController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','userState','errorHandling',
        'referenceData','messageSource',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,userState,errorHandling,
            referenceData,messageSource) {

            $scope.workingUser = undefined;
            $scope.naturalLanguageOptions = undefined;
            var amSaving = false;

            $scope.shouldSpin = function() {
                return undefined == $scope.workingUser || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editUserForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createViewUser($scope.workingUser),
                    breadcrumbs.applyCurrentLocation(breadcrumbs.createEditUser($scope.workingUser))
                ]);
            }

            function refreshUser() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "getUser",
                    [{
                        nickname : $routeParams.nickname
                    }]
                ).then(
                    function(fetchedUser) {

                        $log.info('fetched user; '+fetchedUser.nickname);

                        referenceData.naturalLanguages().then(
                            function(fetchedNaturalLanguages) {

                                function updateNaturalLanguageOptionsTitles() {
                                    _.each($scope.naturalLanguageOptions, function(nl) {
                                        messageSource.get(userState.naturalLanguageCode(), 'naturalLanguage.' + nl.code).then(
                                            function(value) {
                                                nl.title = value;
                                            },
                                            function() {
                                                $log.error('unable to get the localized name for the natural language \''+nl.code+'\'');
                                            }
                                        );
                                    });
                                }

                                $scope.naturalLanguageOptions = _.map(
                                    fetchedNaturalLanguages,
                                    function(nl) {
                                        return {
                                            code : nl.code,
                                            title : nl.name
                                        }
                                    }
                                );

                                updateNaturalLanguageOptionsTitles();

                                // choose the current language as the one for this new user.

                                $scope.workingUser = {
                                    nickname : fetchedUser.nickname,
                                    naturalLanguageOption : _.findWhere(
                                        $scope.naturalLanguageOptions,
                                        { code : fetchedUser.naturalLanguageCode }
                                    )
                                };

                                refreshBreadcrumbItems();

                            },
                            function() { // already logged.
                                errorHandling.navigateToError();
                            }
                        );
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            refreshUser();

            $scope.goSave = function() {

                if($scope.editUserForm.$invalid) {
                    throw 'expected the save of a user to only to be possible if the form is valid';
                }

                amSaving = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "updateUser",
                    [{
                        filter : [ 'NATURALLANGUAGE' ],
                        nickname : $scope.workingUser.nickname,
                        naturalLanguageCode : $scope.workingUser.naturalLanguageOption.code
                    }]
                ).then(
                    function() {
                        $log.info('did update user; '+$scope.workingUser.nickname);

                        // if the currently authenticated user is the one that has just been edited then we should
                        // also update the current language.

                        if(userState.user().nickname == $scope.workingUser.nickname) {
                            userState.naturalLanguageCode($scope.workingUser.naturalLanguageOption.code);
                        }

                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {

                        switch(err.code) {
                            case jsonRpc.errorCodes.VALIDATION:
                                errorHandling.handleValidationFailures(
                                    err.data.validationfailures,
                                    $scope.editUserForm);
                                break;

                            default:
                                errorHandling.handleJsonRpcError(err);
                                break;
                        }

                        amSaving = false;
                    }
                );

            }

        }
    ]
);