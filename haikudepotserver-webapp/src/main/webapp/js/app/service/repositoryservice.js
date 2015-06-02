/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the repositories.</p>
 */

angular.module('haikudepotserver').factory('repository',
    [
        '$log','$q','jsonRpc','userState','errorHandling',
        'constants',
        function(
            $log,$q,jsonRpc,userState,errorHandling,
            constants) {

            var repositories = undefined;

            /**
             * <p>This function will fetch the package from the standard package version request params.</p>
             */

            function getRepositories() {

                var deferred = $q.defer();

                if(repositories) {
                    deferred.resolve(repositories);
                }
                else {

                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_REPOSITORY,
                        'getRepositories',
                        [{}]
                    ).then(
                        function (result) {
                            $log.info('fetched ' + result.repositories.length + ' repositories');
                            deferred.resolve(result);
                        },
                        function (err) {
                            errorHandling.logJsonRpcError(err ? err.error : null);
                            deferred.reject();
                        }
                    );

                }

                return deferred.promise;
            }

            return {

                getRepositories : function() {
                    return getRepositories();
                }

            };

        }
    ]
);