/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service obtains and stores localized messages from the application.</p>
 */

angular.module('haikudepotserver').factory('messageSource',
    [
        '$log','$q','constants','jsonRpc','errorHandling',
        function($log,$q,constants,jsonRpc,errorHandling) {

            /**
             * <p>This ends up being a mapping between the natural language code and a mapping of codes to
             * the messages.</p>
             */

            var naturalLanguagesMessages = {};

            /**
             * <p>When multiple requests come in for the same natural language, they may come in concurrently.  If this
             * is the case then it would be inefficient to go and haul back the same data multiple times.  To avoid
             * this problem, a queue is maintained that keeps track of clients that have asked for the messages and
             * will then service those promises in the queue when the actual data comes in.</p>
             */

            var naturalLanguagesMessagesQueue = {};

            /**
             * <p>This method will go off to the server and pull back the messages for the identified natural language
             * code.  It will return a promise that resolves to the messages as an object.</p>
             */

            function getMessages(naturalLanguageCode) {
                if(!naturalLanguageCode || !(''+naturalLanguageCode).match(/[a-z]{2}/)) {
                    throw Error('the natural language code should be supplied to get messages');
                }

                var deferred = $q.defer();

                if(naturalLanguagesMessages[naturalLanguageCode]) {
                    deferred.resolve(naturalLanguagesMessages[naturalLanguageCode]);
                }
                else {

                    var queue = naturalLanguagesMessagesQueue[naturalLanguageCode];

                    if(!queue) {
                        queue = [];
                        naturalLanguagesMessagesQueue[naturalLanguageCode] = queue;
                    }

                    queue.push(deferred);

                    if(1==queue.length) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_MISCELLANEOUS,
                            'getAllMessages',
                            [
                                { naturalLanguageCode: naturalLanguageCode }
                            ]
                        ).then(
                            function (data) {
                                naturalLanguagesMessages[naturalLanguageCode] = data.messages;

                                _.each(
                                    queue,
                                    function(d) {
                                        d.resolve(naturalLanguagesMessages[naturalLanguageCode]);
                                    }
                                );

                                delete naturalLanguagesMessagesQueue[naturalLanguageCode];
                            },
                            function(err) {
                                errorHandling.logJsonRpcError(err ? err.error : null, 'unable to get the messages from the server for natural language; ' + naturalLanguageCode);
                                deferred.reject(null);
                            }
                        );
                    }
                }

                return deferred.promise;
            }

            /**
             * <p>Given the natural language code and key, this method will return a promise that resolves to the value
             * of the key.  If there is no such value then the key itself will be returned.</p>
             */

            function getMessage(naturalLanguageCode, key) {
                if(!naturalLanguageCode || !(''+naturalLanguageCode).match(/[a-z]{2}/)) {
                    throw Error('the natural language code should be supplied to get a message');
                }

                if(!key || !key.length) {
                    throw Error('a key must be supplied to get a message');
                }

                var deferred = $q.defer();

                getMessages(naturalLanguageCode).then(
                    function(messages) {

                        // if it cannot be found in the requested language then it may be possible to fall back to
                        // looking up the key in english.

                        if(!messages[key] && naturalLanguageCode != constants.NATURALLANGUAGECODE_ENGLISH) {
                            getMessage(constants.NATURALLANGUAGECODE_ENGLISH, key)
                                .then(
                                    function(value) {
                                        deferred.resolve(value);
                                    },
                                    function() {
                                        deferred.reject();
                                    }
                                );
                        }
                        else {
                            deferred.resolve(messages[key] ? messages[key] : key);
                        }
                    },
                    function() {
                        deferred.reject(null);
                    }
                );

                return deferred.promise;
            }

            return {
                get : function(naturalLanguageCode, key) {
                    return getMessage(naturalLanguageCode, key);
                }
            };

        }
    ]
);