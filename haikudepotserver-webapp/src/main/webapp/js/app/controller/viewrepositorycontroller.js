/*
 * Copyright 2014-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositoryController',
    [
        '$scope','$log','$location','$routeParams','$timeout',
        'jsonRpc','constants','userState','errorHandling','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$timeout,
            jsonRpc,constants,userState,errorHandling,breadcrumbs,
            breadcrumbFactory) {

            $scope.repository = undefined;
            $scope.didTriggerImportRepository = false;
            $scope.amShowingInactiveRepositorySources = false;
            var amUpdatingActive = false;
            var amDeletingPassword = false;

            refetchRepository();

            $scope.shouldSpin = function() {
                return !$scope.repository || amUpdatingActive || amDeletingPassword;
            };

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
                    function() {
                        amUpdatingActive = false;
                        $scope.repository.active = flag;

                        if(!flag) {
                            _.each($scope.repository.repositorySources, function(rs) {
                               rs.active = false;
                            });
                        }

                        $log.info('did set the active flag on '+$scope.repository.code+' to '+flag);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canReactivate = function() {
                return $scope.repository && !$scope.repository.active && !amUpdatingActive;
            };

            $scope.canDeactivate = function() {
                return $scope.repository && $scope.repository.active && !amUpdatingActive;
            };

            $scope.goReactivate = function() {
                updateActive(true);
            };

            $scope.goDeactivate = function() {
                updateActive(false);
            };

            $scope.goEdit = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepository($scope.repository));
            };

            $scope.goShowInactiveRepositorySources = function() {
                $scope.amShowingInactiveRepositorySources = true;
                refetchRepository();
            };

            $scope.goAddRepositorySource = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createAddRepositorySource($scope.repository));
            };

            /**
             * This function will stop the display of repository sources that are inactive.  It does not
             * re-fetch from the database, but will instead filter in-memory.
             */

            $scope.goHideInactiveRepositorySources = function() {
                $scope.amShowingInactiveRepositorySources = false;

                $scope.repository.repositorySources = _.filter(
                    $scope.repository.repositorySources,
                    function(rs) {
                        return rs.active;
                    }
                );
            };

            $scope.goDeletePassword = function() {
                amDeletingPassword = true;
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "updateRepository",
                    [{
                        filter: ['PASSWORD'],
                        code: $routeParams.code,
                        passwordClear: null
                    }]
                ).then(
                    function() {
                        $log.info('did delete password for repository [' + $scope.repository.code + ']');
                        $scope.repository.hasPassword = false;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                ).finally(function() {
                    amDeletingPassword = false;
                });
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
                        repositoryCode: $routeParams.code,
                        repositorySourceCode : null
                    }]
                ).then(
                    function() {
                        $log.info('triggered import for repository; '+$scope.repository.code);
                        $scope.didTriggerImportRepository = true;
                        $timeout(function() {
                            $scope.didTriggerImportRepository = false;
                        }, 3000)

                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewRepository($scope.repository))
                ]);
            }

            function refetchRepository() {

                $scope.repository = undefined;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "getRepository",
                    [{
                        code: $routeParams.code,
                        includeInactiveRepositorySources : $scope.amShowingInactiveRepositorySources
                    }]
                ).then(
                    function(result) {
                        $scope.repository = result;

                        // This is required for the repository source to be used with various directives etc...

                        _.each($scope.repository.repositorySources, function(rs) {
                           rs.repositoryCode = result.code;
                        });

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