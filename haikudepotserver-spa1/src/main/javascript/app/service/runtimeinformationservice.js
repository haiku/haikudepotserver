/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').factory('runtimeInformation',
    [
        '$log', '$rootScope', 'remoteProcedureCall', 'errorHandling', 'constants',
        function($log, $rootScope, remoteProcedureCall, errorHandling, constants) {

            var runtimeInformationPromise = undefined;

            $rootScope.$on(
                'userChangeSuccess',
                function() { runtimeInformationPromise = undefined; }
            );

            return {
                "getRuntimeInformation": function() {

                    if (!runtimeInformationPromise) {
                        runtimeInformationPromise = remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_MISCELLANEOUS,
                            'get-runtime-information'
                        ).then(
                            function(result) {
                                $log.info('have fetched the runtime information');
                                return result;
                            },
                            errorHandling.handleRemoteProcedureCallError
                    );
                    }

                    return runtimeInformationPromise;
                }
            }
        }
    ]
);
