/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositoryController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','userState','errorHandling','breadcrumbs',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,userState,errorHandling,breadcrumbs) {

            $scope.breadcrumbItems = undefined;
            $scope.repository = undefined;
            var amUpdatingActive = false;

            refetchRepository();

            $scope.shouldSpin = function() {
                return undefined == $scope.repository;
            }

            function updateActive(flag) {

                amUpdatingActive = true;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_REPOSITORY,
                        "updateRepository",
                        [{
                            code : $routeParams.code,
                            active : flag,
                            filter : [ 'ACTIVE' ]
                        }]
                    ).then(
                    function(result) {
                        amUpdatingActive = false;
                        $scope.repository.active = flag;
                        $log.info('did set the active flag on '+$scope.repository.code+' to '+flag);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canActivate = function() {
                return $scope.repository && !$scope.repository.active && !amUpdatingActive;
            }

            $scope.canDeactivate = function() {
                return $scope.repository && $scope.repository.active && !amUpdatingActive;
            }

            $scope.goActivate = function() {
                updateActive(true);
            }

            $scope.goDeactivate = function() {
                updateActive(false);
            }

            /**
             * <p>This function will initiate an import of a repository.  These run sequentially so it may not happen
             * immediately; it may be queued to go later.</p>
             */

            $scope.goTriggerImport = function() {

            }

            function refreshBreadcrumbItems() {
                $scope.breadcrumbItems = [
                    breadcrumbs.createMore(),
                    breadcrumbs.createListRepositories(),
                    breadcrumbs.createViewRepository($scope.repository)
                ];
            }

            function refetchRepository() {

                $scope.repository = undefined;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_REPOSITORY,
                        "getRepository",
                        [{ code: $routeParams.code }]
                    ).then(
                    function(result) {
                        $scope.repository = result;
                        $log.info('found '+$scope.repository.code+' repository');
                        refreshBreadcrumbItems();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

        }
    ]
);