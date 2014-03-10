/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service maintains a list of reference data objects that can be re-used in the system.  This prevents the
 * need to keep going back to the server to ge this material; it can be cached locally.</p>
 */

angular.module('haikudepotserver').factory('referenceData',
    [
        '$log','$q','jsonRpc','constants','errorHandling',
        function($log, $q, jsonRpc,constants,errorHandling) {

            var architectures = undefined;
            var pkgCategories = undefined;

            var ReferenceData = {

                pkgCategories : function() {

                    var deferred = $q.defer();

                    if(pkgCategories) {
                        deferred.resolve(pkgCategories);
                    }
                    else {
                        jsonRpc
                            .call(
                                constants.ENDPOINT_API_V1_MISCELLANEOUS,'getAllPkgCategories',[{}]
                            )
                            .then(
                            function(data) {
                                pkgCategories = data.pkgCategories;
                                deferred.resolve(pkgCategories);
                            },
                            function(err) {
                                errorHandling.logJsonRpcError(err,'issue obtaining the list of pkg categories');
                                deferred.reject(err);
                            }
                        );
                    }

                    return deferred.promise;
                },

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
                                errorHandling.logJsonRpcError(err,'issue obtaining the list of architectures');
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