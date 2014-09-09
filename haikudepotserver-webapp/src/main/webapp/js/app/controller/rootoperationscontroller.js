/*
 * Copyright 2014, Andrew Lindesay
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
                breadcrumbFactory.createRootOperations(),
            ]);

            $scope.didDeriveAndStoreUserRatingsForAllPkgs = false;
            $scope.deriveAndStoreUserRatingsForAllPkgsTimeout = undefined;

            $scope.didSynchronizeUsers = false;
            $scope.didSynchronizeUsersTimeout = undefined;

            $scope.goDeriveAndStoreUserRatingsForAllPkgs = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING,
                    "deriveAndStoreUserRatingsForAllPkgs",
                    [{}]
                ).then(
                    function() {
                        $log.info('requested derive and store user ratings for all pkgs');
                        $scope.didDeriveAndStoreUserRatingsForAllPkgs = true;

                        if($scope.deriveAndStoreUserRatingsForAllPkgsTimeout) {
                            $timeout.cancel($scope.deriveAndStoreUserRatingsForAllPkgsTimeout);
                        }

                        $scope.deriveAndStoreUserRatingsForAllPkgsTimeout = $timeout(function() {
                            $scope.didDeriveAndStoreUserRatingsForAllPkgs = false;
                            $scope.deriveAndStoreUserRatingsForAllPkgsTimeout = undefined;
                        }, 3000);
                    },
                    function(err) {
                        $log.error('unable to derive and store user ratings for all pkgs');
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            $scope.goSynchronizeUsers = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "synchronizeUsers",
                    [{}]
                ).then(
                    function() {
                        $log.info('requested synchronize users');
                        $scope.didSynchronizeUsers = true;

                        if($scope.didSynchronizeUsersTimeout) {
                            $timeout.cancel($scope.didSynchronizeUsersTimeout);
                        }

                        $scope.didSynchronizeUsersTimeout = $timeout(function() {
                            $scope.didSynchronizeUsers = false;
                            $scope.didSynchronizeUsersTimeout = undefined;
                        }, 3000);
                    },
                    function(err) {
                        $log.error('unable to synchronize users');
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            /**
             * <p>This is outside of the normal breadcrumb navigation system because it is a
             * developers' feature.</p>
             */

            $scope.goPaginationControlPlayground = function() {
                $location.path('/paginationcontrolplayground').search({});
            }

            $scope.goRuntimeInformation = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createRuntimeInformation());
            }

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
            }
        }
    ]
);