/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositorySourceMirrorController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','errorHandling','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,errorHandling,breadcrumbs,
            breadcrumbFactory) {

            $scope.repositorySourceMirror = undefined;
            var amUpdatingActive = false;

            refetchRepositorySourceMirror();

            $scope.shouldSpin = function() {
                return !$scope.repositorySourceMirror;
            };

            function updateActive(flag) {

                amUpdatingActive = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "updateRepositorySourceMirror",
                    [{
                        code : $scope.repositorySourceMirror.code,
                        active : flag,
                        filter : [ 'ACTIVE' ]
                    }]
                ).then(
                    function() {
                        amUpdatingActive = false;
                        $scope.repositorySourceMirror.active = flag;
                        $log.info('did set the active flag on ' + $scope.repositorySourceMirror.code+' to ' + flag);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canReactivate = function() {
                return $scope.repositorySourceMirror &&
                    !$scope.repositorySourceMirror.active &&
                    !amUpdatingActive;
            };

            $scope.canDeactivate = function() {
                return $scope.repositorySourceMirror &&
                    $scope.repositorySourceMirror.active &&
                    !$scope.repositorySourceMirror.isPrimary &&
                    !amUpdatingActive;
            };

            $scope.goReactivate = function() {
                updateActive(true);
            };

            $scope.goDeactivate = function() {
                updateActive(false);
            };

            // $scope.goEdit = function() {
            //     breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepositorySource($scope.repositorySource));
            // };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.createViewRepository($scope.repositorySourceMirror.repositorySource.repository),
                    breadcrumbFactory.createViewRepositorySource($scope.repositorySourceMirror.repositorySource),
                    breadcrumbFactory.applyCurrentLocation(
                        breadcrumbFactory.createViewRepositorySourceMirror($scope.repositorySourceMirror))
                ]);
            }

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            function refetchRepositorySourceMirror() {

                $scope.repositorySourceMirror = undefined;
                var repositorySourceMirror = undefined;

                fnChain([
                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepositorySourceMirror",
                            [{ code: $routeParams.repositorySourceMirrorCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepositorySource",
                            [{ code: repositorySourceMirror.repositorySourceCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror.repositorySource = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepository",
                            [{ code: repositorySourceMirror.repositorySource.repositoryCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror.repositorySource.repository = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        $scope.repositorySourceMirror = repositorySourceMirror;
                        refreshBreadcrumbItems();
                        fnChain(chain);
                    }

                    ]);
            }

        }
    ]
);