/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ListJobsController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','userState',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,userState,
            breadcrumbs,breadcrumbFactory,errorHandling) {

            function refreshBreadcrumbItems() {
                if($routeParams.nickname) {
                    var user = { nickname: $routeParams.nickname };

                    if(userState.user().nickname == $routeParams.nickname) {
                        breadcrumbs.mergeCompleteStack([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createViewUser(user),
                            breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createListJobs(user))
                        ]);
                    }
                    else {
                        breadcrumbs.mergeCompleteStack([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createListUsers(),
                            breadcrumbFactory.createViewUser(user),
                            breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createListJobs(user))
                        ]);
                    }
                }
                else {
                    breadcrumbs.mergeCompleteStack([
                        breadcrumbFactory.createHome(),
                        breadcrumbFactory.createRootOperations(),
                        breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createListJobs())
                    ]);
                }
            }

            refreshBreadcrumbItems();

            var PAGESIZE = 15;

            $scope.jobs = {
                items: undefined,
                offset: 0,
                max: PAGESIZE,
                total: undefined
            };

            function clearJobs() {
                $scope.jobs.items = undefined;
                $scope.jobs.total = undefined;
                $scope.jobs.offset = 0;
            }

            var amFetchingJobs = false;

            refetchJobsAtFirstPage();

            $scope.goRefetchJobsAtFirstPage = function() {
                refetchJobsAtFirstPage();
            };

            $scope.shouldSpin = function() {
                return amFetchingJobs;
            };

            function refetchJobsAtFirstPage() {
                $scope.jobs.offset = 0;
                refetchJobs();
            }

            function refetchJobs() {

                amFetchingJobs = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_JOB,
                    "searchJobs",
                    [{
                        ownerUserNickname: $routeParams.nickname,
                        expression: null,
                        expressionType: 'CONTAINS',
                        offset: $scope.jobs.offset,
                        limit: $scope.jobs.max
                    }]
                ).then(
                    function (result) {

                        // the job label directive requires a data structure with the nickname in it
                        // so re-format the owner user as appropriate.

                        $scope.jobs.items = _.map(
                            result.items,
                            function (item) {
                                if (item.ownerUserNickname) {
                                    item.ownerUser = {
                                        nickname: item.ownerUserNickname
                                    };
                                }

                                return item;
                            }
                        );
                        $scope.jobs.total = result.total;
                        $log.info('found ' + result.items.length + ' jobs');
                        amFetchingJobs = false;
                    },
                    function (err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // ---- EVENTS

            $scope.$watch('jobs.offset', function() {
                refetchJobs();
            });

        }
    ]
);