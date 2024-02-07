/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositorySourceController',
    [
        '$scope','$log','$location','$routeParams','$timeout',
        'remoteProcedureCall','constants','userState','errorHandling','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$timeout,
            remoteProcedureCall,constants,userState,errorHandling,breadcrumbs,
            breadcrumbFactory) {

            $scope.didTriggerImportRepositorySource = false;
            $scope.repositorySource = undefined;
            $scope.amShowingInactiveRepositorySourceMirrors = false;
            var amUpdatingActive = false;

            refetchRepositorySource();

            $scope.shouldSpin = function () {
                return !$scope.repositorySource;
            };

            function updateActive(flag) {

                amUpdatingActive = true;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "update-repository-source",
                    {
                        code : $scope.repositorySource.code,
                        active : flag,
                        filter : [ 'ACTIVE' ]
                    }
                ).then(
                    function () {
                        amUpdatingActive = false;
                        $scope.repositorySource.active = flag;
                        $log.info('did set the active flag on '+$scope.repositorySource.code+' to '+flag);
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

            $scope.isAuthenticated = function () {
                return !!userState.user();
            };

            $scope.canTriggerImport = function () {
                return $scope.repositorySource &&
                    $scope.repositorySource.active &&
                    $scope.repositorySource.repository.active;
            };

            $scope.canReactivate = function () {
                return $scope.repositorySource && !$scope.repositorySource.active && !amUpdatingActive;
            };

            $scope.canDeactivate = function () {
                return $scope.repositorySource && $scope.repositorySource.active && !amUpdatingActive;
            };

            $scope.goReactivate = function () {
                updateActive(true);
            };

            $scope.goDeactivate = function () {
                updateActive(false);
            };

            $scope.goEdit = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditRepositorySource($scope.repositorySource));
            };

            $scope.goViewRepositorySourceMirror = function (repositorySourceMirror) {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewRepositorySourceMirror(repositorySourceMirror));
            };

            $scope.goIncludeInactiveRepositorySourceMirrors = function () {
                $scope.amShowingInactiveRepositorySourceMirrors = true;
                refetchRepositorySource();
            };

            $scope.goAddRepositorySourceMirror = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory
                    .createAddRepositorySourceMirror($scope.repositorySource));
            };

            /**
             * <p>This function will initiate an import of a repository.  These run sequentially so it may not happen
             * immediately; it may be queued to go later.</p>
             */

            $scope.goTriggerImport = function () {
                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "trigger-import-repository",
                    {
                        repositoryCode: $scope.repositorySource.repository.code,
                        repositorySourceCodes : [ $scope.repositorySource.code ]
                    }
                ).then(
                    function () {
                        $log.info('triggered import for repository source; '+$scope.repositorySource.code);
                        $scope.didTriggerImportRepositorySource = true;
                        $timeout(function() {
                            $scope.didTriggerImportRepositorySource = false;
                        }, 3000)

                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            $scope.goQueuePkgDumpExportJob = function () {
                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG_JOB,
                    "queue-pkg-dump-export-job",
                    {
                        repositorySourceCode: $scope.repositorySource.code,
                        naturalLanguageCode : userState.naturalLanguageCode()
                    }
                ).then(
                    function (data) {
                        $log.info('queued pkg dump export job');
                        breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:data.guid }))
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

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

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_REPOSITORY,
                    "get-repository-source",
                    {
                        code: $routeParams.repositorySourceCode,
                        includeInactiveRepositorySourceMirrors: $scope.amShowingInactiveRepositorySourceMirrors
                    }
                ).then(
                    function (repositorySourceResult) {

                        // each of the mirrors that are related in the result need to link
                        // back to the repository source.

                        _.each(
                            repositorySourceResult.repositorySourceMirrors,
                            function (m) { m.repositorySource = repositorySourceResult; }
                        );

                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_REPOSITORY,
                            "get-repository",
                            { code: $routeParams.repositoryCode }
                        ).then(
                            function (repositoryResult) {
                                repositorySourceResult.repository = repositoryResult;
                                $scope.repositorySource = repositorySourceResult;
                                refreshBreadcrumbItems();
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    },
                    function(err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

        }
    ]
);
