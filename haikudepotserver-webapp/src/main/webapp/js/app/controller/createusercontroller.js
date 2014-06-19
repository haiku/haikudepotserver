/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'CreateUserController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','errorHandling','referenceData','userState','messageSource','breadcrumbs',
        function(
            $scope,$log,$location,
            jsonRpc,constants,errorHandling,referenceData,userState,messageSource,breadcrumbs) {

            $scope.captchaToken = undefined;
            $scope.captchaImageUrl = undefined;
            $scope.amSaving = false;
            $scope.naturalLanguageOptions = undefined;
            $scope.newUser = {
                nickname : undefined,
                passwordClear : undefined,
                passwordClearRepeated : undefined,
                captchaResponse : undefined
            };

            regenerateCaptcha();

            // this function will derive these options from the reference data of the natural languages.  It will then
            // also blend-in the localized names of the languages as well.  It can do this asynchronously in the
            // background by taking the database supplied (unlocalized) names first of all and then updating the data
            // objects later.

            function refreshNaturalLanguageOptions() {

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

                referenceData.naturalLanguages().then(
                    function(data) {

                        $scope.naturalLanguageOptions = _.map(data, function(nl) {
                            return {
                                code : nl.code,
                                title : nl.name
                            }
                        });

                        updateNaturalLanguageOptionsTitles();

                        // choose the current language as the one for this new user.

                        $scope.newUser.naturalLanguageOption = _.findWhere(
                            $scope.naturalLanguageOptions,
                            { code : userState.naturalLanguageCode() }
                        );

                    },
                    function() { // already logged.
                        errorHandling.navigateToError();
                    }
                )
            }

            refreshNaturalLanguageOptions();

            $scope.shouldSpin = function() {
                return undefined == $scope.captchaToken ||
                    $scope.amSaving ||
                    undefined == $scope.naturalLanguageOptions; // have to be async loaded
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.createUserForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.applyCurrentLocation(breadcrumbs.createAddUser())
                ]);
            }

            function regenerateCaptcha() {

                $scope.captchaToken = undefined;
                $scope.captchaImageUrl = undefined;
                $scope.newUser.captchaResponse = undefined;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_CAPTCHA,
                    "generateCaptcha",
                    [{}]
                ).then(
                    function(result) {
                        $scope.captchaToken = result.token;
                        $scope.captchaImageUrl = 'data:image/png;base64,'+result.pngImageDataBase64;
                        refreshBreadcrumbItems();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            // When you go to save, if the user types the wrong captcha response then they will get an error message
            // letting them know this, but there is no natural mechanism for this invalid state to get unset.  For
            // this reason, any change to the response text field will be taken to trigger this error state to be
            // removed.

            $scope.captchaResponseDidChange = function() {
                $scope.createUserForm.captchaResponse.$setValidity('badresponse',true);
            };

            $scope.nicknameDidChange = function() {
                $scope.createUserForm.nickname.$setValidity('notunique',true);
            };

            $scope.passwordsChanged = function() {
                $scope.createUserForm.passwordClearRepeated.$setValidity(
                    'repeat',
                        !$scope.newUser.passwordClear
                        || !$scope.newUser.passwordClearRepeated
                        || $scope.newUser.passwordClear == $scope.newUser.passwordClearRepeated);
            };

            // This function will take the data from the form and will create the user from this data.

            $scope.goCreateUser = function() {

                if($scope.createUserForm.$invalid) {
                    throw 'expected the creation of a user only to be possible if the form is valid';
                }

                $scope.amSaving = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "createUser",
                    [{
                        nickname : $scope.newUser.nickname,
                        passwordClear : $scope.newUser.passwordClear,
                        captchaToken : $scope.captchaToken,
                        captchaResponse : $scope.newUser.captchaResponse,
                        naturalLanguageCode : $scope.newUser.naturalLanguageOption.code
                    }]
                ).then(
                    function() {
                        $log.info('created new user; '+$scope.newUser.nickname);

                        // get rid of the breadcrumb for creating the user as there is no sense in that any more
                        // and push the authenticate user so the user can then login with their nickname and
                        // password that they have just nominated.

                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.applySearch(
                                breadcrumbs.createAuthenticate(),
                                {
                                    nickname : $scope.newUser.nickname,
                                    didCreate : true
                                }
                            )
                        ]);
                    },
                    function(err) {

                        regenerateCaptcha();
                        $scope.amSaving = false;

                        switch(err.code) {

                            case jsonRpc.errorCodes.VALIDATION:

                                // actually there shouldn't really be any validation problems except that the nickname
                                // may already be in use.  We can deal with this one and then pass the rest to the
                                // default handler.

                                if(err.data && err.data.validationfailures) {
                                    _.each(err.data.validationfailures, function(vf) {
                                        var model = $scope.createUserForm[vf.property];

                                        if(model) {
                                            model.$setValidity(vf.message, false);
                                        }
                                        else {
                                            $log.error('other validation failures exist; will invoke default handling');
                                            errorHandling.handleJsonRpcError(err);
                                        }
                                    })
                                }

                                break;

                            case jsonRpc.errorCodes.CAPTCHABADRESPONSE:
                                $log.error('the user has mis-interpreted the captcha; will lodge an error into the form and then populate a new one for them');
                                $scope.createUserForm.captchaResponse.$setValidity('badresponse',false);
                                break;

                            default:
                                errorHandling.handleJsonRpcError(err);
                                break;
                        }
                    }
                );


            }

        }
    ]
);