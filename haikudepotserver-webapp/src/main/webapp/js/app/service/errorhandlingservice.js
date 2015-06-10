/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is used to centralize the handling of errors; providing consistent handling and error feedback.</p>
 */

angular.module('haikudepotserver').factory('errorHandling',
    [ '$log','$location','breadcrumbs','constants',
        function($log,$location,breadcrumbs,constants) {

            var haveNavigatedToError = false;

            /**
             * <p>Local storage might contain some data about the user state; this should
             * be removed so that the user is effectively logged out.</p>
             */

            function clearLocalStorage() {
                if(window.localStorage) {
                    window.localStorage.removeItem(constants.STORAGE_TOKEN_KEY);
                }
            }

            /**
             * <p>This function will exit the AngularJS environment into vanilla HTML.</p>
             */

            function navigateToError(code) {
                if (!haveNavigatedToError) {
                    breadcrumbs.reset();
                    var query = code ? '?jrpcerrorcd=' + code : '';
                    window.location.href = '/error' + query;
                    haveNavigatedToError = true;
                }
            }

            var ErrorHandlingService = {

                navigateToError : function(code) {
                    navigateToError(code);
                },

                logJsonRpcError : function(jsonRpcErrorEnvelope, message) {
                    var prefix = message ? message + ' - json-rpc error; ' : 'json-rpc error; ';

                    if(!jsonRpcErrorEnvelope) {
                        $log.error(prefix+'cause is unknown as no error was available in the envelope');
                    }
                    else {

                        if (jsonRpcErrorEnvelope.error && !jsonRpcErrorEnvelope.code) {
                            $log.error('illegal state; provided bad envelope; should be the \'error\' component of the response rather than the entire response -- will try to correct for now');
                            jsonRpcErrorEnvelope = jsonRpcErrorEnvelope.error;
                        }

                        var logCode = jsonRpcErrorEnvelope.code ? jsonRpcErrorEnvelope.code : '?';
                        var logMessage = jsonRpcErrorEnvelope.message ? jsonRpcErrorEnvelope.message : '?';
                        $log.error(prefix + 'code:' + logCode + ", msg:" + logMessage);
                    }
                },

                /**
                 * <p>When a JSON-RPC failure occurs, this method can be invoked to provide uniform logging and
                 * handling.</p>
                 */

                handleJsonRpcError : function(jsonRpcErrorEnvelope) {
                    ErrorHandlingService.logJsonRpcError(jsonRpcErrorEnvelope);
                    var code = jsonRpcErrorEnvelope ? jsonRpcErrorEnvelope.code : undefined;

                    if(code == -32803) { // TODO; should be a constant
                        clearLocalStorage();
                    }

                    navigateToError(code);
                },

                /**
                 * <p>This situation arises when somebody navigates to a page that does not exist.</p>
                 */

                handleUnknownLocation : function() {
                    $log.error('unknown location; ' + $location.path());
                    navigateToError();
                },

                /**
                 * <p>Splay validation failures into the form.  The validation failures are objects that are
                 * returned as part of the error envelope in RPC invocations.  This method will return true
                 * if all of the validation errors were able to be assigned to models in the form.</p>
                 */

                handleValidationFailures : function(validationFailures, form) {

                    if(!form) {
                        throw Error('a form must be provided to populate in the validation failures');
                    }

                    var result = true;

                    if(validationFailures) {
                        _.each(validationFailures, function(vf) {
                            if(vf.property && vf.property.length) {
                                var model = form[vf.property];

                                if(model) {
                                    model.$setValidity(vf.message, false);
                                }
                                else {
                                    result = false;
                                }
                            }
                        })
                    }

                    return result;
                }

            };

            return ErrorHandlingService;

        }
    ]
);

// note the use of the injector service here is used to avoid cyclic dependencies
// with directly injecting $location.

angular.module('haikudepotserver').config([
    '$provide',
    function($provide) {
        $provide.decorator('$exceptionHandler', [
            '$delegate','$injector',
            function($delegate, $injector) {
                return function(exception, cause) {
                    var $location = $injector.get('$location');
                    var $log = $injector.get('$log');
                    var breadcrumbs = $injector.get('breadcrumbs');

                    if ($log) {
                        $log.error('unhandled error; ' + exception);
                    }

                    $delegate(exception,cause);

                    breadcrumbs.reset();
                    window.location.href = '/error';
                }
            }
        ]);

    }
]);