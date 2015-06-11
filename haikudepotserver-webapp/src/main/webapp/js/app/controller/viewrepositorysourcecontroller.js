/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositorySourceController',
    [
        '$scope','$log','$location','$routeParams','$timeout',
        'jsonRpc','constants','userState','errorHandling','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$timeout,
            jsonRpc,constants,userState,errorHandling,breadcrumbs,
            breadcrumbFactory) {

            $scope.didTriggerImportRepositorySource = false;
            $scope.repositorySource = undefined;
            var amUpdatingActive = false;

            refetchRepositorySource();

            $scope.shouldSpin = function() {
                return undefined == $scope.repositorySource;
            };

            function updateActive(flag) {

                amUpdatingActive = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "updateRepositorySource",
                    [{
                        code : $scope.repositorySource.code,
                        active : flag,
                        filter : [ 'ACTIVE' ]
                    }]
                ).then(
                    function() {
                        amUpdatingActive = false;
                        $scope.repositorySource.active = flag;
                        $log.info('did set the active flag on '+$scope.repositorySource.code+' to '+flag);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canTriggerImport = function() {
                return $scope.repositorySource &&
                    $scope.repositorySource.active &&
                    $scope.repositorySource.repository.active;
            };

            $scope.canReactivate = function() {
                return $scope.repositorySource && !$scope.repositorySource.active && !amUpdatingActive;
            };

            $scope.canDeactivate = function() {
                return $scope.repositorySource && $scope.repositorySource.active && !amUpdatingActive;
            };

            $scope.goReactivate = function() {
                updateActive(true);
            };

            $scope.goDeactivate = function() {
                updateActive(false);
            };

            $scope.goEdit = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepositorySource($scope.repositorySource));
            };

            /**
             * <p>This function will initiate an import of a repository.  These run sequentially so it may not happen
             * immediately; it may be queued to go later.</p>
             */

            $scope.goTriggerImport = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "triggerImportRepository",
                    [{
                        repositoryCode: $scope.repositorySource.repository.code,
                        repositorySourceCodes : [ $scope.repositorySource.code ]
                    }]
                ).then(
                    function() {
                        $log.info('triggered import for repository source; '+$scope.repositorySource.code);
                        $scope.didTriggerImportRepositorySource = true;
                        $timeout(function() {
                            $scope.didTriggerImportRepositorySource = false;
                        }, 3000)

                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            //
            //$scope.goEdit = function() {
            //    breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepository($scope.repository));
            //};

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.createViewRepository($scope.repositorySource.repository),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewRepositorySource($scope.repositorySource))
                ]);
            }

            function refetchRepositorySource() {

                $scope.repositorySource = undefined;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "getRepositorySource",
                    [{ code: $routeParams.repositorySourceCode }]
                ).then(
                    function(repositorySourceResult) {

                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepository",
                            [{ code: $routeParams.repositoryCode }]
                        ).then(
                            function(repositoryResult) {
                                repositorySourceResult.repository = repositoryResult;
                                $scope.repositorySource = repositorySourceResult;
                                refreshBreadcrumbItems();
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

        }
    ]
);