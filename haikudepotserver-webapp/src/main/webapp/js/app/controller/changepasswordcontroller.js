/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ChangePasswordController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','breadcrumbFactory','userState','errorHandling',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,breadcrumbFactory,userState,errorHandling) {

            $scope.user = undefined;
            $scope.captchaToken = undefined;
            $scope.captchaImageUrl = undefined;
            $scope.changePasswordData = {
                captchaResponse : undefined,
                oldPasswordClear : undefined,
                newPasswordClear : undefined,
                newPasswordClearRepeated : undefined
            };

            var amChangingPassword = false;

            $scope.shouldSpin = function() {
                return undefined == $scope.user || amChangingPassword;
            };

            $scope.requiresOldPassword = function() {
                return (!$scope.user || !userState.user() || $scope.user.nickname == userState.user().nickname);
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.changePasswordForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            refreshUser();
            regenerateCaptcha();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewUser($scope.user),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createChangePassword($scope.user))
                ]);
            }

            function refreshUser() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "getUser",
                    [{ nickname : $routeParams.nickname }]
                ).then(
                    function(result) {
                        $scope.user = result;
                        refreshBreadcrumbItems();
                        $log.info('fetched user; '+result.nickname);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            function regenerateCaptcha() {

                $scope.captchaToken = undefined;
                $scope.captchaImageUrl = undefined;
                $scope.changePasswordData.captchaResponse = undefined;

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

            // When you go to action, if the user types the wrong captcha response then they will get an error message
            // letting them know this, but there is no natural mechanism for this invalid state to get unset.  For
            // this reason, any change to the response text field will be taken to trigger this error state to be
            // removed.

            $scope.captchaResponseDidChange = function() {
                $scope.changePasswordForm.captchaResponse.$setValidity('badresponse',true);
            };

            $scope.newPasswordsChanged = function() {
                $scope.changePasswordForm.newPasswordClearRepeated.$setValidity(
                    'repeat',
                        !$scope.changePasswordData.newPasswordClear
                        || !$scope.changePasswordData.newPasswordClearRepeated
                        || $scope.changePasswordData.newPasswordClear == $scope.changePasswordData.newPasswordClearRepeated);
            };

            $scope.oldPasswordChanged = function() {
                $scope.changePasswordForm.oldPasswordClear.$setValidity('mismatched',true);
            };

            $scope.goChangePassword = function() {

                if($scope.changePasswordForm.$invalid) {
                    throw Error('expected the change password of a user only to be possible if the form is valid');
                }

                $scope.amChangingPassword = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "changePassword",
                    [{
                        nickname : $scope.user.nickname,
                        oldPasswordClear : $scope.changePasswordData.oldPasswordClear,
                        newPasswordClear : $scope.changePasswordData.newPasswordClear,
                        captchaToken : $scope.captchaToken,
                        captchaResponse : $scope.changePasswordData.captchaResponse
                    }]
                ).then(
                    function() {

                        $log.info('did change password for user; '+$scope.user.nickname);

                        if(userState.user().nickname == $scope.user.nickname) {
                            userState.user(null); // logout
                            breadcrumbs.resetAndNavigate([
                                breadcrumbFactory.createHome(),
                                breadcrumbFactory.applySearch(
                                    breadcrumbFactory.createAuthenticate(),
                                    {
                                        nickname: $scope.user.nickname,
                                        didChangePassword: 'true'
                                    }
                                )
                            ]);
                        }
                        else {
                            breadcrumbs.popAndNavigate();
                        }
                    },
                    function(err) {
                        regenerateCaptcha();
                        $scope.amSaving = false;

                        switch(err.code) {

                            // should not be any validation failures that we need to deal with here.


                            case jsonRpc.errorCodes.VALIDATION:

                                // actually there shouldn't really be any validation problems except that the oldPasswordClear
                                // not match to the user for which the change password operation is being performed.

                                if(err.data && err.data) {
                                    _.each(err.data, function(vf) {
                                        var model = $scope.changePasswordForm[vf.property];

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
                                $scope.changePasswordForm.captchaResponse.$setValidity('badresponse',false);
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