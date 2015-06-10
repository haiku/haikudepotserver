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