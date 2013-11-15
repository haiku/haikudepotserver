/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service obtains and stores localized messages from the application.</p>
 */

angular.module('haikudepotserver').factory('messageSource',
    [
        '$log','$q','constants','jsonRpc',
        function($log,$q,constants,jsonRpc) {

            var MessageSource = {

                messages : undefined,

                get : function(key) {

                    var deferred = $q.defer();

                    if(MessageSource.messages) {
                        deferred.resolve(MessageSource.messages[key]);
                    }
                    else {
                        jsonRpc.call(
                                constants.ENDPOINT_API_V1_MISCELLANEOUS,
                                'getAllMessages',
                                [{}]
                            ).then(
                            function(data) {
                                MessageSource.messages = data.messages;
                                deferred.resolve(MessageSource.messages[key] ? MessageSource.messages[key] : key);
                            },
                            function(err) {
                                $log.warn('unable to get the messages from the server');
                                deferred.reject(null);
                            }
                        );
                    }

                    return deferred.promise;
                }

            };

            return MessageSource;

        }
    ]
);