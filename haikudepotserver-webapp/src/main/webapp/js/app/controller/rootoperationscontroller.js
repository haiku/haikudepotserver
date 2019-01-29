/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'RootOperationsController',
    [
        '$scope','$log','$location','$timeout',
        'jsonRpc','constants','userState',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        function(
            $scope,$log,$location,$timeout,
            jsonRpc,constants,userState,
            breadcrumbs,breadcrumbFactory,errorHandling) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.createRootOperations()
            ]);

            $scope.didActions = {
            };

            function showDidAction(name) {
                $log.info('did action; ' + name);

                var didAction = $scope.didActions[name];

                if(didAction) {
                    $timeout.cancel(didAction.timeout);
                }

                $scope.didActions[name] = {
                    timeout : $timeout(function() {
                        $scope.didActions[name] = undefined;
                    }, 3000)
                };
            }

            $scope.goQueuePkgVersionPayloadLengthPopulationJob = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    "queuePkgVersionPayloadLengthPopulationJob",
                    [{}]
                ).then(
                    function() {
                        showDidAction('queuePkgVersionPayloadLengthPopulationJob');
                    },
                    function(err) {
                        $log.error('unable to queue pkg version payload length population job');
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            $scope.goDeriveAndStoreUserRatingsForAllPkgs = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING,
                    "deriveAndStoreUserRatingsForAllPkgs",
                    [{}]
                ).then(
                    function() {
                        showDidAction('deriveAndStoreUserRatingsForAllPkgs');
                    },
                    function(err) {
                        $log.error('unable to derive and store user ratings for all pkgs');
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            /**
             * <p>This is outside of the normal breadcrumb navigation system because it is a
             * developers' feature.</p>
             */

            $scope.goPaginationControlPlayground = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createPaginationPlayground());
            };

            $scope.goPkgCategoryCoverageImportSpreadsheet = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createPkgCategoryCoverageImportSpreadsheet());
            };

            $scope.goPkgIconArchiveImport = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createPkgIconArchiveImport());
            };

            $scope.goPkgScreenshotArchiveImport = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createPkgScreenshotArchiveImport());
            };

            // -------------------
            // TEST ERROR HANDLING TESTING

            $scope.goRaiseExceptionInLocalRuntime = function() {
                throw Error('test exception in javascript environment');
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
            };
        }
    ]
);