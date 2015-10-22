/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the repositories.</p>
 */

angular.module('haikudepotserver').factory('repositoryService',
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
                            repositories = result.repositories;
                            deferred.resolve(result.repositories);
                        },
                        function (err) {
                            errorHandling.logJsonRpcError(err);
                            deferred.reject();
                        }
                    );

                }

                return deferred.promise;
            }

            function preferentialSearchRepositories(repositories) {
                if(undefined != repositories) {
                    if(!repositories || !repositories.length) {
                        if (window.localStorage) {
                            window.localStorage.removeItem(constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY);
                        }
                    }
                    else {
                        if (window.localStorage) {
                            window.localStorage.setItem(
                                constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY,
                                angular.toJson(_.pluck(repositories,'code'))
                            );
                        }
                    }
                }

                return getRepositories().then(
                    function(allRepositories) {
                        var result;

                        if(!allRepositories || !allRepositories.length) {
                            throw Error('no repositories can be found');
                        }

                        if (window.localStorage) {
                            var codesStr = window.localStorage.getItem(constants.STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY);

                            if(codesStr && codesStr.length) {
                                var codes = angular.fromJson(codesStr);

                                if(codes && codes.length) {
                                    result = _.filter(allRepositories, function (r) { return _.contains(codes, r.code) });
                                }
                            }
                        }

                        if(!result || !result.length) {
                            result = _.filter(allRepositories, function(r) { return r.code == constants.REPOSITORY_CODE_DEFAULT; });
                        }

                        if(!result || !result.length) {
                            throw Error('unable to establish the preferential search repositories');
                        }

                        return result;
                    }
                );

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