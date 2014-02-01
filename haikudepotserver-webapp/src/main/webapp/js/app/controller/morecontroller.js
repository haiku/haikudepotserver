/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'MoreController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','userState',
        'breadcrumbs','errorHandling',
        function(
            $scope,$log,$location,
            jsonRpc,constants,userState,
            breadcrumbs,errorHandling) {

            $scope.breadcrumbItems = [ breadcrumbs.createMore() ];
            $scope.serverStartTimestamp = undefined;
            $scope.serverProjectVersion = '...';

            $scope.goHome = function() {
                $location.path("/").search({});
            }

            // -------------------
            // USER

            function refreshAuthorization() {

                function disallowAll() {
                    $scope.canGoRuntimeInformation = false;
                }

                var u = userState.user();

                if(!u || !u.nickname) {
                    disallowAll();
                }
                else {
                    jsonRpc.call(
                            constants.ENDPOINT_API_V1_USER,
                            "getUser",
                            [{
                                nickname : u.nickname
                            }]
                        ).then(
                        function(result) {
                            if(result.isRoot) {
                                $scope.canGoRuntimeInformation = true;
                            }
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                }
            }

            refreshAuthorization();

            // -------------------
            // RUNTIME INFORMATION

            function refreshRuntimeInformation() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_MISCELLANEOUS,
                        "getRuntimeInformation",
                        [{}]
                    ).then(
                    function(result) {
                        $scope.serverProjectVersion = result.projectVersion;
                        $log.info('have fetched the runtime information');
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            refreshRuntimeInformation();

            $scope.canGoRuntimeInformation = false;

            $scope.goRuntimeInformation = function() {
                $location.path('/runtimeinformation').search({});
            }

        }
    ]
);