/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'CompletePasswordResetController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','userState','errorHandling',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,userState,errorHandling) {

            if(userState.user()) {
                throw 'it is not possible to complete password reset with an authenticated user';
            }

            var Status = {
                IDLE : 'IDLE',
                UNDERTAKING : 'UNDERTAKING',
                DONE : 'DONE'
            };

            $scope.status = Status.IDLE;
            $scope.user = undefined;
            $scope.captchaToken = undefined;
            $scope.captchaImageUrl = undefined;
            $scope.completePasswordResetData = {
                captchaResponse : undefined,
                newPasswordClear : undefined,
                newPasswordClearRepeated : undefined
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.captchaToken || Status.UNDERTAKING == $scope.status;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.completePasswordResetForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            $scope.goAuthenticate = function() {
                breadcrumbs.resetAndNavigate([
                    breadcrumbs.createHome(),
                    breadcrumbs.createAuthenticate()
                ]);
            };

            regenerateCaptcha();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createCompletePasswordReset($routeParams.token)
                ]);
            }

            function regenerateCaptcha() {

                $scope.captchaToken = undefined;
                $scope.captchaImageUrl = undefined;
                $scope.completePasswordResetData.captchaResponse = undefined;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_CAPTCHA,
                    "generateCaptcha",
                    [{}]
                ).then(
                    function(result) {
                        $scope.captchaToken = result.token;
                        //noinspection JSUnresolvedVariable
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
                $scope.completePasswordResetForm.captchaResponse.$setValidity('badresponse',true);
            };

            $scope.newPasswordsChanged = function() {
                $scope.completePasswordResetForm.newPasswordClearRepeated.$setValidity(
                    'repeat',
                        !$scope.completePasswordResetData.newPasswordClear
                        || !$scope.completePasswordResetData.newPasswordClearRepeated
                        || $scope.completePasswordResetData.newPasswordClear == $scope.completePasswordResetData.newPasswordClearRepeated);
            };

            $scope.goResetPassword = function() {

                if($scope.completePasswordResetData.$invalid) {
                    throw 'expected the reset password only to be possible if the form is valid';
                }

                $scope.status = Status.UNDERTAKING;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "completePasswordReset",
                    [{
                        token : $routeParams.token,
                        passwordClear : $scope.completePasswordResetData.newPasswordClear,
                        captchaToken : $scope.captchaToken,
                        captchaResponse : $scope.completePasswordResetData.captchaResponse
                    }]
                ).then(
                    function() {
                        $log.info('did reset password');
                        $scope.status = Status.DONE;
                    },
                    function(err) {

                        regenerateCaptcha();
                        $scope.status = Status.IDLE;

                        switch(err.code) {

                            case jsonRpc.errorCodes.CAPTCHABADRESPONSE:
                                $log.error('the user has mis-interpreted the captcha; will lodge an error into the form and then populate a new one for them');
                                $scope.completePasswordResetForm.captchaResponse.$setValidity('badresponse',false);
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