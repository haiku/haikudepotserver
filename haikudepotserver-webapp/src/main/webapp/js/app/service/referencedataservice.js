/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service maintains a list of reference data objects that can be re-used in the system.  This prevents the
 * need to keep going back to the server to ge this material; it can be cached locally.</p>
 */

angular.module('haikudepotserver').factory('referenceData',
    [
        '$log','$q','jsonRpc','constants',
        function($log, $q, jsonRpc,constants) {

            var architectures = undefined;

            var ReferenceData = {

                /**
                 * <p>This function returns a promise to return the architecture identified by the code.</p>
                 */

                architecture : function(code) {
                    var deferred = $q.defer();

                    ReferenceData.architectures().then(
                        function(data) {
                            var a = _.find(data, function(item) {
                                return item.code == code;
                            });

                            if(a) {
                                deferred.resolve(a);
                            }
                            else {
                                deferred.reject();
                            }
                        },
                        function() {
                            deferred.reject();
                        }
                    )

                    return deferred.promise;
                },

                /**
                 * <p>This function will return all of the architectures that are available.  It will return a
                 * promise.</p>
                 * @returns {*}
                 */

                architectures : function() {

                    var deferred = $q.defer();

                    if(architectures) {
                        deferred.resolve(architectures);
                    }
                    else {
                        jsonRpc
                            .call(
                                constants.ENDPOINT_API_V1_MISCELLANEOUS,'getAllArchitectures',[{}]
                            )
                            .then(
                            function(data) {
                                architectures = data.architectures;
                                deferred.resolve(architectures);
                            },
                            function(err) {
                                deferred.reject(null);
                            }
                        );
                    }

                    return deferred.promise;
                }

            };

            return ReferenceData;

        }
    ]
);