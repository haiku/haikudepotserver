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

            /**
             * <p>This variable holds a cache of the reference data by method name.</p>
             */

            var repository = {};

            var queues = {};

            /**
             * <p>This method will get the data requested by deriving a method name on the misc api.  The 'what' value
             * has a "getAll..." prefixed and this forms the correct method name to use.  A system using a queue of
             * promises is used to avoid two concurrent requests being made for the same data.  A list of data found in
             * the response object can be found by using the 'what' value for a key.</p>
             */

            function getData(what) {

                if(!what || !what.length) {
                    throw Error('the method name is expected in order to get reference data');
                }

                var deferred = $q.defer();

                if(repository[what]) {
                    deferred.resolve(repository[what]);
                }
                else {

                    var queue = queues[what];

                    if(!queue) {
                        queue = [];
                        queues[what] = queue;
                    }

                    queue.push(deferred);

                    jsonRpc
                        .call(
                        constants.ENDPOINT_API_V1_MISCELLANEOUS,
                        'getAll' + what.charAt(0).toUpperCase() + what.substring(1),
                        [{}]
                    ).then(
                        function(data) {

                            var list = data[what];

                            repository[what] = list;

                            _.each(
                                queues[what],
                                function(queueItem) {
                                    queueItem.resolve(list);
                                }
                            );

                            delete queues[what];
                        },
                        function(err) {
                            errorHandling.logJsonRpcError(err ? err.error : null,'issue obtaining data for the misc method; '+what);
                            deferred.reject(err);
                        }
                    );

                }

                return deferred.promise;
            }

            return {

                /**
                 * <p>This relates to the ATOM feed sources and although it is hard-coded, it is still
                 * supplied from this reference data service in order to maintain consistency and to
                 * allow for easier enhancement later.</p>
                 */

                feedSupplierTypes : function() {
                    var deferred = $q.defer();
                    deferred.resolve(_.map(
                        [ 'CREATEDPKGVERSION', 'CREATEDUSERRATING' ],
                        function(item) {
                            return {
                                code : item
                            };
                        }
                    ));
                    return deferred.promise;
                },

                naturalLanguages : function() {
                    return getData('naturalLanguages');
                },

                prominences : function() {
                    return getData('prominences');
                },

                pkgCategories : function() {
                    return getData('pkgCategories');
                },

                architectures : function() {
                    return getData('architectures');
                },

                userRatingStabilities : function() {
                    return getData('userRatingStabilities');
                },

                architecture : function(code) {
                    var deferred = $q.defer();

                    getData('architectures').then(
                        function(allArchitectures) {
                            var theArchitecture = _.find(allArchitectures, function(anArchitecture) {
                                return anArchitecture.code == code;
                            });

                            if(theArchitecture) {
                                deferred.resolve(theArchitecture);
                            }
                            else {
                                deferred.reject();
                            }
                        },
                        function() {
                            deferred.reject();
                        }
                    );

                    return deferred.promise;
                }

            };

        }
    ]
);