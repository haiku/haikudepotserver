/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'CreateUserController',
    [
        '$scope','$log','$location',
        'remoteProcedureCall','constants','errorHandling','referenceData','userState',
        'messageSource','breadcrumbs','breadcrumbFactory',
        function(
            $scope, $log, $location,
            remoteProcedureCall, constants, errorHandling, referenceData, userState,
            messageSource, breadcrumbs, breadcrumbFactory) {

            // TODO: use the data from the server rather than hard code it here
            $scope.userNicknamePattern = ('' + constants.PATTERN_USER_NICKNAME).replace(/^\//,'').replace(/\/$/,'');
            $scope.captchaToken = undefined;
            $scope.captchaImageUrl = undefined;
            $scope.amSaving = false;
            $scope.userUsageConditions = undefined;
            $scope.newUser = {
                nickname : undefined,
                email : undefined,
                passwordClear : undefined,
                passwordClearRepeated : undefined,
                captchaResponse : undefined,
                naturalLanguageCode : userState.naturalLanguageCode()
            };

            regenerateCaptcha();

            $scope.shouldSpin = function() {
                return undefined === $scope.userUsageConditions ||
                    undefined === $scope.captchaToken ||
                    $scope.amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.createUserForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAddUser())
                ]);
            }

            function fetchUserUsageConditions() {
                return remoteProcedureCall.call(constants.ENDPOINT_API_V2_USER, "get-user-usage-conditions")
                    .then(
                        function (userUsageConditionsData) {
                            $scope.userUsageConditions = userUsageConditionsData;
                        },
                        errorHandling.handleRemoteProcedureCallError
                    );
            }

            fetchUserUsageConditions();

            function regenerateCaptcha() {

                $scope.captchaToken = undefined;
                $scope.captchaImageUrl = undefined;
                $scope.newUser.captchaResponse = undefined;

                remoteProcedureCall.call(constants.ENDPOINT_API_V2_CAPTCHA, "generate-captcha").then(
                    function (result) {
                        $scope.captchaToken = result.token;
                        $scope.captchaImageUrl = 'data:image/png;base64,'+result.pngImageDataBase64;
                        refreshBreadcrumbItems();
                    },
                    errorHandling.handleRemoteProcedureCallError
                );
            }

            // When you go to save, if the user types the wrong captcha response then they will get an error message
            // letting them know this, but there is no natural mechanism for this invalid state to get unset.  For
            // this reason, any change to the response text field will be taken to trigger this error state to be
            // removed.

            $scope.captchaResponseDidChange = function () {
                $scope.createUserForm.captchaResponse.$setValidity('badresponse', true);
            };

            $scope.nicknameDidChange = function () {
                $scope.createUserForm.nickname.$setValidity('notunique', true);
            };

            $scope.passwordsChanged = function () {
                $scope.createUserForm.passwordClearRepeated.$setValidity(
                    'repeat',
                        !$scope.newUser.passwordClear
                        || !$scope.newUser.passwordClearRepeated
                        || $scope.newUser.passwordClear === $scope.newUser.passwordClearRepeated);
            };

            /**
             * This function will return true if the state of the form and data
             * is such that the user is able to proceed with saving the new
             * user.
             */

            $scope.canCreateUser = function() {
                return !($scope.createUserForm.$invalid) &&
                    $scope.newUser.userUsageConditionsIsMinimumAgeExceeded &&
                    $scope.newUser.userUsageConditionsDocumentAgreed;
            };

            // This function will take the data from the form and will create the user from this data.

            $scope.goCreateUser = function () {

                if ($scope.createUserForm.$invalid) {
                    throw Error('expected the creation of a user only to be possible if the form is valid');
                }

                $scope.amSaving = true;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_USER,
                    "create-user",
                    {
                        nickname : $scope.newUser.nickname,
                        email : $scope.newUser.email,
                        passwordClear : $scope.newUser.passwordClear,
                        captchaToken : $scope.captchaToken,
                        captchaResponse : $scope.newUser.captchaResponse,
                        naturalLanguageCode : $scope.newUser.naturalLanguageCode,
                        userUsageConditionsCode: $scope.userUsageConditions.code
                    }
                ).then(
                    function () {
                        $log.info('created new user; '+$scope.newUser.nickname);

                        if (userState.user()) {
                            breadcrumbs.pop();
                            breadcrumbs.pushAndNavigate(
                                breadcrumbFactory.createViewUser({ nickname : $scope.newUser.nickname })
                            );
                        }
                        else {

                            // get rid of the breadcrumb for creating the user as there is no sense in that any more
                            // and push the authenticate user so the user can then login with their nickname and
                            // password that they have just nominated.

                            breadcrumbs.resetAndNavigate([
                                breadcrumbFactory.createHome(),
                                breadcrumbFactory.applySearch(
                                    breadcrumbFactory.createAuthenticate(),
                                    {
                                        nickname: $scope.newUser.nickname,
                                        didCreate: true
                                    }
                                )
                            ]);
                        }
                    },
                    function (err) {

                        regenerateCaptcha();
                        $scope.amSaving = false;

                        switch (err.code) {

                            case remoteProcedureCall.errorCodes.VALIDATION:

                                // actually there shouldn't really be any validation problems except that the nickname
                                // may already be in use.  We can deal with this one and then pass the rest to the
                                // default handler.
                                _.each(err.data || [], function (vf) {
                                    var model = $scope.createUserForm[vf.key];

                                    if(model) {
                                        model.$setValidity(vf.value, false);
                                    }
                                    else {
                                        $log.error('other validation failures exist; will invoke default handling');
                                        errorHandling.handleRemoteProcedureCallError(err);
                                    }
                                });

                                break;

                            case remoteProcedureCall.errorCodes.CAPTCHABADRESPONSE:
                                $log.error('the user has mis-interpreted the captcha; will lodge an error into the form and then populate a new one for them');
                                $scope.createUserForm.captchaResponse.$setValidity('badresponse',false);
                                break;

                            default:
                                errorHandling.handleRemoteProcedureCallError(err);
                                break;
                        }
                    }
                );


            }

        }
    ]
);
