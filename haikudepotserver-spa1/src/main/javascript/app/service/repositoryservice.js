/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the repositories.</p>
 */

angular.module('haikudepotserver').factory('repositoryService',
    [
        '$log', '$q', 'remoteProcedureCall', 'userState', 'errorHandling',
        'constants', 'runtimeInformation', 'localStorageProxy',
        function(
            $log, $q, remoteProcedureCall, userState, errorHandling,
            constants, runtimeInformation, localStorageProxy) {

            var repositoriesPromise = undefined;

            /**
             * <p>This function will fetch the package from the standard package version request params.</p>
             */

            function getRepositories() {
                if(!repositoriesPromise) {
                    repositoriesPromise = remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        'get-repositories'
                    ).then(
                        function successCallback(data) {
                            return data.repositories;
                        },
                        function errorCallback(err) {
                            errorHandling.logRemoteProcedureCallError(err);
                            return $q.reject();
                        }
                    )
                }

                return repositoriesPromise;
            }

            function preferentialSearchRepositories(repositories) {
                if (undefined !== repositories) {
                    if (!repositories || !repositories.length) {
                        localStorageProxy.removeItem(constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY);
                    }
                    else {
                        localStorageProxy.setItem(
                            constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY,
                            angular.toJson(_.pluck(repositories,'code'))
                        );
                    }
                }

                var runtimeInformationData = undefined;

                return runtimeInformation.getRuntimeInformation().then(
                    function(result) {
                        runtimeInformationData = result;
                    }
                ).then(function() {
                    return getRepositories();
                }).then(function(allRepositories) {
                    var result;

                    if (!allRepositories || !allRepositories.length) {
                        throw Error('no repositories can be found');
                    }

                    var codesStr = localStorageProxy.getItem(constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY);

                    if (codesStr && codesStr.length) {
                        var codes = angular.fromJson(codesStr);

                        if(codes && codes.length) {
                            result = _.filter(allRepositories, function (r) { return _.contains(codes, r.code) });
                        }
                    }

                    if (!result || !result.length) {
                        result = _.filter(
                            allRepositories,
                            function(r) {
                                return r.code === runtimeInformationData.defaults.repositoryCode;
                            });
                    }

                    if (!result || !result.length) {
                        throw Error('unable to establish the preferential search repositories');
                    }

                    return result;
                });
            }

            return {

                getRepositories : function() {
                    return getRepositories();
                },

                /**
                 * This function will return those repositories that the user would like to search.  This might be
                 * from a prior search and stored in local storage or might be via some other means.  The method
                 * will return a promise that is resolved to a list of repositories.  The function is a getter +
                 * setter and will take-up the values given if they are supplied.
                 */

                preferentialSearchRepositories : function(repositories) {
                    return preferentialSearchRepositories(repositories);
                }

            };

        }
    ]
);
