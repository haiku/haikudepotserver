/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service obtains and stores localized messages from the application.</p>
 */

angular.module('haikudepotserver')
    .factory('naturalLanguageMessagesCache',[
        '$cacheFactory',
        function($cacheFactory) {
            return $cacheFactory('naturalLanguageMessages',{ capacity:10 });
        }
    ])
    .factory('messageSource',
    [
        '$log','$q','constants','remoteProcedureCall','errorHandling','naturalLanguageMessagesCache',
        function($log,$q,constants,remoteProcedureCall,errorHandling,naturalLanguageMessagesCache) {

            /**
             * <p>This method will go off to the server and pull back the messages for the identified natural language
             * code.  It will return a promise that resolves to the messages as an object.</p>
             */

            function getMessages(naturalLanguageCode) {
                if(!naturalLanguageCode || !(''+naturalLanguageCode).match(/[a-z]{2}/)) {
                    throw Error('the natural language code should be supplied to get messages');
                }

                var result = naturalLanguageMessagesCache.get(naturalLanguageCode);

                if(!result) {
                    result = remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_MISCELLANEOUS,
                        'get-all-messages',
                        { naturalLanguageCode: naturalLanguageCode }
                    ).then(
                        function successCallback(data) {
                            return _.reduce(
                              data.messages,
                              function (result, item) {
                                  var itemObj = {};
                                  itemObj[item.key] = item.value;
                                  return _.extend(result, itemObj);
                              },
                              {}
                            );
                        },
                        function errorCallback(err) {
                            errorHandling.logRemoteProcedureCallError(err, 'unable to get the messages from the server for natural language; ' + naturalLanguageCode);
                            return $q.reject();
                        }
                    );

                    naturalLanguageMessagesCache.put(naturalLanguageCode, result);
                }

                return result;
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

                return getMessages(naturalLanguageCode).then(
                    function successCallback(messages) {
                        if(!messages[key] && naturalLanguageCode != constants.NATURALLANGUAGECODE_ENGLISH) {
                            return getMessage(constants.NATURALLANGUAGECODE_ENGLISH, key);
                        }

                        return messages[key] ? messages[key] : key;
                    }
                );
            }

            return {
                get : getMessage
            };

        }
    ]
);
