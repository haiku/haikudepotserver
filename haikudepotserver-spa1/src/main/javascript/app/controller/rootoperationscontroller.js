/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'RootOperationsController',
    [
        '$scope','$log','$location','$timeout',
        'remoteProcedureCall','constants','userState',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        function(
            $scope,$log,$location,$timeout,
            remoteProcedureCall,constants,userState,
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

            $scope.goQueuePkgVersionPayloadDataPopulationJob = function() {
                remoteProcedureCall.call(constants.ENDPOINT_API_V2_PKG_JOB, "queue-pkg-version-payload-data-population-job").then(
                    function() {
                        showDidAction('queuePkgVersionPayloadDataPopulationJob');
                    },
                    function(err) {
                        $log.error('unable to queue pkg version payload data population job');
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            $scope.goDeriveAndStoreUserRatingsForAllPkgs = function() {
                remoteProcedureCall.call(constants.ENDPOINT_API_V2_USERRATING, "derive-and-store-user-ratings-for-all-pkgs").then(
                    function() {
                        showDidAction('deriveAndStoreUserRatingsForAllPkgs');
                    },
                    function(err) {
                        $log.error('unable to derive and store user ratings for all pkgs');
                        errorHandling.handleRemoteProcedureCallError(err);
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

            $scope.goPkgLocalizationImport = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createPkgLocalizationImport());
            }

            // -------------------
            // TEST ERROR HANDLING TESTING

            $scope.goRaiseExceptionInLocalRuntime = function() {
                throw Error('test exception in javascript environment');
            };

            $scope.goRaiseExceptionInServerRuntime = function() {
                remoteProcedureCall.call(constants.ENDPOINT_API_V2_MISCELLANEOUS, "raise-exception").then(
                    function() {
                        $log.error('the exception raised on the server runtime -> should not have reached this point');
                    },
                    errorHandling.handleRemoteProcedureCallError
                );
            };
        }
    ]
);
