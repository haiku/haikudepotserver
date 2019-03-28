/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').factory('runtimeInformation',
    [
        '$log', '$rootScope', 'jsonRpc', 'errorHandling', 'constants',
        function($log, $rootScope, jsonRpc, errorHandling, constants) {

            var runtimeInformationPromise = undefined;

            $rootScope.$on(
                'userChangeSuccess',
                function() { runtimeInformationPromise = undefined; }
            );

            return {
                "getRuntimeInformation": function() {

                    if (!runtimeInformationPromise) {
                        runtimeInformationPromise = jsonRpc.call(
                            constants.ENDPOINT_API_V1_MISCELLANEOUS,
                            'getRuntimeInformation', [{}]
                        ).then(
                            function(result) {
                                $log.info('have fetched the runtime information');
                                return result;
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                            );
                    }

                    return runtimeInformationPromise;
                }
            }
        }
    ]
);
