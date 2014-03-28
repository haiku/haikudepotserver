/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'RuntimeInformationController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','userState',
        'breadcrumbs','errorHandling',
        function(
            $scope,$log,$location,
            jsonRpc,constants,userState,
            breadcrumbs,errorHandling) {

            $scope.breadcrumbItems = [
                breadcrumbs.createMore(),
                breadcrumbs.createRuntimeInformation()
            ];

            $scope.serverStartTimestamp = undefined;
            $scope.versions = {
                angularFull : angular.version.full,
                serverProject : '...',
                serverJava : '...'
            };

            function refreshRuntimeInformation() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_MISCELLANEOUS,
                        "getRuntimeInformation",
                        [{}]
                    ).then(
                    function(result) {
                        $scope.versions.serverProject = result.projectVersion;
                        $scope.versions.serverJava = result.javaVersion;
                        $scope.serverStartTimestamp = result.startTimestamp;
                        $log.info('have fetched the runtime information');
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            refreshRuntimeInformation();

            // -------------------
            // ERROR HANDLING TESTING

            $scope.goRaiseExceptionInLocalRuntime = function() {
                throw 'test exception in javascript environment';
            };

            $scope.goRaiseExceptionInServerRuntime = function() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_MISCELLANEOUS,
                        "raiseException",
                        [{}]
                    ).then(
                    function() {
                        $log.error('the exception raised on the server runtime -> should not have reached this point');
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }
        }
    ]
);