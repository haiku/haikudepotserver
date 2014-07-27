/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the screenshots for a package.</p>
 */

angular.module('haikudepotserver').factory('pkg',
    [
        '$log','$q','jsonRpc','userState','errorHandling',
        'constants',
        function(
            $log,$q,jsonRpc,userState,errorHandling,
            constants) {

            /**
             * <p>This function will fetch the package from the standard package version request params.</p>
             */

            function getPkgWithSpecificVersion(pkgName, versionCoordinates, architectureCode, incrementCounter) {

                if(!pkgName||!pkgName.length) {
                    throw Error('pkg name must be supplied');
                }

                if(!versionCoordinates||!versionCoordinates.major) {
                    throw Error('version coordinates must be supplied');
                }

                if(!architectureCode||!architectureCode.length) {
                    throw Error('architecture code must be supplied');
                }

                var deferred = $q.defer();

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    'getPkg',
                    [{
                        name : pkgName,
                        versionType : 'SPECIFIC',
                        incrementViewCounter : !!incrementCounter,
                        architectureCode : architectureCode,
                        naturalLanguageCode: userState.naturalLanguageCode(),
                        major: versionCoordinates.major,
                        minor : versionCoordinates.minor,
                        micro : versionCoordinates.micro,
                        preRelease : versionCoordinates.preRelease,
                        revision : versionCoordinates.revision
                    }]
                ).then(
                    function(result) {
                        $log.info('fetched '+result.name+' pkg');
                        deferred.resolve(result);
                    },
                    function(err) {
                        errorHandling.logJsonRpcError(err ? err.error : null);
                        deferred.reject();
                    }
                );

                return deferred.promise;
            }

            /**
             * <p>This assumes some standard names in the route params and pull out the details required to
             * get the package and its version.</p>
             * @param routeParams
             */

            function getPkgWithSpecificVersionFromRouteParams(routeParams, incrementCounter) {

                if(!routeParams) {
                    throw Error('route params expected');
                }

                return getPkgWithSpecificVersion(
                    routeParams.name,
                    {
                        major : routeParams.major,
                        minor : routeParams.minor,
                        micro : routeParams.micro,
                        preRelease : routeParams.preRelease,
                        revision : routeParams.revision
                    },
                    routeParams.architectureCode,
                    incrementCounter);
            }

            return {
                getPkgWithSpecificVersionFromRouteParams : function(routeParams, incrementCounter) {
                    return getPkgWithSpecificVersionFromRouteParams(routeParams, incrementCounter);
                }
            };

        }
    ]
);