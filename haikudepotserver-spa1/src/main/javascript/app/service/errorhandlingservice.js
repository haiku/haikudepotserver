/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is used to centralize the handling of errors; providing consistent handling and error feedback.</p>
 */

angular.module('haikudepotserver').factory('errorHandling',
    [ '$http', "$q", '$log', '$location', 'breadcrumbs', 'constants',
        function($http, $q, $log, $location, breadcrumbs, constants) {

            var MAX_COUNTLOGMESSAGESSENTTOSERVER = 5;

            var haveNavigatedToError = false;

            /**
             * <p>This value is used to keep track of how often error log messages are sent over to the
             * server.  This is a safety feature to avoid too many requests into the application
             * server.</p>
             * @type {number}
             */

            var countLogMessagesSentToServer = 0;

            /**
             * <p>When validation problems come back from the server to the client, for example malformed
             * data entered into a form, then the validation problems need to be splayed into the
             * form so that the user can see the problems.  This function will manage that process.</p>
             * @param validationFailures is taken from a JSON-RPC error response envelope.
             * @param form is the AngularJS form object that the errors should be written into.
             * @returns {boolean} false if any of the validation failures were not able to be taken-up.
             */

            function relayValidationFailuresIntoForm(validationFailures, form) {
                if(!form) {
                    throw Error('a form must be provided to populate in the validation failures');
                }

                var result = true;

                _.each(validationFailures||[], function(vf) {
                    if(vf.key && vf.key.length) {
                        var model = form[vf.key];

                        if (model) {
                            model.$setValidity(vf.value, false);
                        }
                        else {
                            result = false;
                        }
                    }
                });

                return result;
            }

            /**
             * <p>This function will send logged messages to the application server.  It will
             * only send so many messages before it stops in order to avoid overloading the
             * server if there is a widespread problem going on.</p>
             */

            function sendErrorLogMessageToServer(message) {

                if(message && message.length && countLogMessagesSentToServer < MAX_COUNTLOGMESSAGESSENTTOSERVER) {

                    countLogMessagesSentToServer++;

                    return $http({
                        cache: false,
                        method: 'POST',
                        url: '/__log/capture',
                        headers: { 'Content-Type' : 'text/plain' },
                        data: message
                    });
                }

                return $q.resolve();
            }

            function logAndSendErrorMessageToServer(message) {
                $log.error(message);
                return sendErrorLogMessageToServer(message);
            }

            function logAndSendRemoteProcedureCallErrorToServer(remoteProcedureCallErrorEnvelope, message) {
                var prefix = message ? message + ' - rpc error; ' : 'rpc error; ';

                if(!remoteProcedureCallErrorEnvelope) {
                    return logAndSendErrorMessageToServer(prefix+'cause is unknown as no error was available in the envelope');
                }

                var p = $q.resolve(true);

                if (remoteProcedureCallErrorEnvelope.error && !remoteProcedureCallErrorEnvelope.code) {
                    // TODO - remove at some point in the future.
                    var msg = 'illegal state; provided bad envelope; should be the \'error\' component of the response rather than the entire response -- will try to correct for now';
                    remoteProcedureCallErrorEnvelope = remoteProcedureCallErrorEnvelope.error;
                    p = p.then(logAndSendErrorMessageToServer(msg));
                }

                var logCode = remoteProcedureCallErrorEnvelope.code ? remoteProcedureCallErrorEnvelope.code : '?';
                var logMessage = remoteProcedureCallErrorEnvelope.message ? remoteProcedureCallErrorEnvelope.message : '?';
                return p.then(logAndSendErrorMessageToServer(prefix + 'code:' + logCode + ", msg:" + logMessage));
            }

            /**
             * <p>Local storage might contain some data about the user state; this should
             * be removed so that the user is effectively logged out.  Note that this does
             * not go through the local storage proxy because if there is an error then it
             * is not too clear what is and what is not working; for safety's sake, this
             * will go direct to the window.localStorage.</p>
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
                    window.location.href = '/__error' + query;
                    haveNavigatedToError = true;
                }
            }

            return {

                navigateToError : function(code) {
                    navigateToError(code);
                },

                handleException: function(exception, cause) {
                    return logAndSendErrorMessageToServer(
                        'unhandled error; ' + exception
                    ).then(
                        function() {
                            breadcrumbs.reset();
                            window.location.href = '/__error';
                        },
                        function() {
                            console.error("an issue has arisen handling an exception");
                            breadcrumbs.reset();
                            window.location.href = '/__error';
                        }
                    )
                },

                logRemoteProcedureCallError : function(remoteProcedureCallErrorEnvelope, message) {
                    return logAndSendRemoteProcedureCallErrorToServer(remoteProcedureCallErrorEnvelope,message);
                },

                /**
                 * <p>When a JSON-RPC failure occurs, this method can be invoked to provide uniform logging and
                 * handling.</p>
                 */

                handleRemoteProcedureCallError : function(remoteProcedureCallErrorEnvelope) {
                    return logAndSendRemoteProcedureCallErrorToServer(
                        remoteProcedureCallErrorEnvelope
                    ).then(function() {
                        var code = remoteProcedureCallErrorEnvelope ? remoteProcedureCallErrorEnvelope.code : undefined;

                        if (code == -32803) { // TODO; should be a constant
                            clearLocalStorage();
                        }

                        navigateToError(code);
                    });
                },

                /**
                 * <p>This situation arises when somebody navigates to a page that does not exist.</p>
                 */

                handleUnknownLocation : function() {
                    logAndSendErrorMessageToServer(
                        'unknown location; ' + $location.path()
                    ).then(function() {
                        navigateToError();
                    });
                },

                /**
                 * <p>Splay validation failures into the form.  The validation failures are objects that are
                 * returned as part of the error envelope in RPC invocations.  This method will return true
                 * if all of the validation errors were able to be assigned to models in the form.</p>
                 */

                relayValidationFailuresIntoForm : function(validationFailures, form) {
                    return relayValidationFailuresIntoForm(validationFailures, form);
                }

            };

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
                    $delegate(exception, cause);

                    try {
                        var errorHandling = $injector.get('errorHandling');

                        if (errorHandling) {
                            errorHandling.handleException(exception, cause);
                        } else {
                            if (window && window.console) {
                                window.console.error('? unhandled error; ' + exception);
                            }
                        }
                    } catch (e) {
                        console.error("error in error handler; " + e);
                    }
                }
            }
        ]);

    }
]);
