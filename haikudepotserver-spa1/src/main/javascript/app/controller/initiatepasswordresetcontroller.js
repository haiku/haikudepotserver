/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'InitiatePasswordResetController',
    [
        '$scope','$log',
        'remoteProcedureCall','constants','errorHandling','userState','breadcrumbs','breadcrumbFactory',
        function(
            $scope,$log,
            remoteProcedureCall,constants,errorHandling,userState,breadcrumbs,breadcrumbFactory) {

            if (userState.user()) {
                throw Error('it is not possible to reset the password if a user is presently authenticated.');
            }

            var Status = {
                IDLE : 'IDLE',
                UNDERTAKING : 'UNDERTAKING',
                DONE : 'DONE'
            };

            $scope.status = Status.IDLE;
            $scope.captchaToken = undefined;
            $scope.captchaImageUrl = undefined;
            $scope.workingInitiatePasswordReset = {
                captchaResponse : undefined,
                email : undefined
            };

            regenerateCaptcha();

            $scope.shouldSpin = function() {
                return undefined === $scope.captchaToken ||
                    $scope.status === Status.UNDERTAKING;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.initiatePasswordResetForm && $scope.initiatePasswordResetForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createInitiatePasswordReset())
                ]);
            }

            function regenerateCaptcha() {

                $scope.captchaToken = undefined;
                $scope.captchaImageUrl = undefined;
                $scope.workingInitiatePasswordReset.captchaResponse = undefined;

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

            $scope.captchaResponseDidChange = function() {
                $scope.initiatePasswordResetForm.captchaResponse.$setValidity('badresponse',true);
            };

            // This function will take the data from the form and will create the user from this data.

            $scope.goInitiatePasswordReset = function() {

                if ($scope.initiatePasswordResetForm.$invalid) {
                    throw Error('expected the initiation of password reset only to be possible if the form is valid');
                }

                $scope.status = Status.UNDERTAKING;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_USER,
                    "initiate-password-reset",
                    {
                        email : $scope.workingInitiatePasswordReset.email,
                        passwordClear : $scope.workingInitiatePasswordReset.passwordClear,
                        captchaToken : $scope.captchaToken,
                        captchaResponse : $scope.workingInitiatePasswordReset.captchaResponse
                    }
                ).then(
                    function () {
                        $log.info('initiated password reset');
                        $scope.status = Status.DONE;
                    },
                    function (err) {

                        regenerateCaptcha();

                        switch (err.code) {

                            case remoteProcedureCall.errorCodes.CAPTCHABADRESPONSE:
                                $log.error('the user has mis-interpreted the captcha; will lodge an error into the form and then populate a new one for them');
                                $scope.initiatePasswordResetForm.captchaResponse.$setValidity('badresponse',false);
                                $scope.status = Status.IDLE;
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
