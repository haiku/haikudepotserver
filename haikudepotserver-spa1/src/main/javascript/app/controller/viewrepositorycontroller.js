/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositoryController',
    [
        '$scope','$log','$location','$routeParams','$timeout',
        'remoteProcedureCall','constants','userState','errorHandling','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$timeout,
            remoteProcedureCall,constants,userState,errorHandling,breadcrumbs,
            breadcrumbFactory) {

            $scope.repository = undefined;
            $scope.didTriggerImportRepository = false;
            $scope.amShowingInactiveRepositorySources = false;
            var amUpdatingActive = false;
            var amDeletingPassword = false;

            refetchRepository();

            $scope.shouldSpin = function () {
                return !$scope.repository || amUpdatingActive || amDeletingPassword;
            };

            function updateActive(flag) {

                amUpdatingActive = true;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "update-repository",
                    {
                        code : $routeParams.code,
                        active : flag,
                        filter : [ 'ACTIVE' ]
                    }
                ).then(
                    function () {
                        amUpdatingActive = false;
                        $scope.repository.active = flag;

                        if (!flag) {
                            _.each($scope.repository.repositorySources, function(rs) {
                               rs.active = false;
                            });
                        }

                        $log.info('did set the active flag on '+$scope.repository.code+' to '+flag);
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

            $scope.canReactivate = function () {
                return $scope.repository && !$scope.repository.active && !amUpdatingActive;
            };

            $scope.canDeactivate = function () {
                return $scope.repository && $scope.repository.active && !amUpdatingActive;
            };

            $scope.goReactivate = function () {
                updateActive(true);
            };

            $scope.goDeactivate = function() {
                updateActive(false);
            };

            $scope.goEdit = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepository($scope.repository));
            };

            $scope.goShowInactiveRepositorySources = function () {
                $scope.amShowingInactiveRepositorySources = true;
                refetchRepository();
            };

            $scope.goAddRepositorySource = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createAddRepositorySource($scope.repository));
            };

            /**
             * This function will stop the display of repository sources that are inactive.  It does not
             * re-fetch from the database, but will instead filter in-memory.
             */

            $scope.goHideInactiveRepositorySources = function () {
                $scope.amShowingInactiveRepositorySources = false;

                $scope.repository.repositorySources = _.filter(
                    $scope.repository.repositorySources,
                    function (rs) {
                        return rs.active;
                    }
                );
            };

            $scope.goDeletePassword = function () {
                amDeletingPassword = true;
                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "update-repository",
                    {
                        filter: ['PASSWORD'],
                        code: $routeParams.code,
                        passwordClear: null
                    }
                ).then(
                    function () {
                        $log.info('did delete password for repository [' + $scope.repository.code + ']');
                        $scope.repository.hasPassword = false;
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                ).finally(function () {
                    amDeletingPassword = false;
                });
            };

            /**
             * <p>This function will initiate an import of a repository.  These run sequentially so it may not happen
             * immediately; it may be queued to go later.</p>
             */

            $scope.goTriggerImport = function() {
              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "trigger-import-repository",
                    {
                        repositoryCode: $routeParams.code,
                        repositorySourceCode : null
                    }
                ).then(
                    function () {
                        $log.info('triggered import for repository; '+$scope.repository.code);
                        $scope.didTriggerImportRepository = true;
                        $timeout(function () {
                            $scope.didTriggerImportRepository = false;
                        }, 3000)

                    },
                    function(err) {
                        errorHandling.handleRemoteProcedureCallError(err);
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

              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "get-repository",
                    {
                        code: $routeParams.code,
                        includeInactiveRepositorySources : $scope.amShowingInactiveRepositorySources
                    }
                ).then(
                    function (result) {
                        $scope.repository = result;

                        // This is required for the repository source to be used with various directives etc...

                        _.each($scope.repository.repositorySources, function (rs) {
                           rs.repositoryCode = result.code;
                        });

                        $log.info('found '+$scope.repository.code+' repository');
                        refreshBreadcrumbItems();
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

        }
    ]
);
