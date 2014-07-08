/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ListRepositoriesController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        function(
            $scope,$log,$location,
            jsonRpc,constants,
            breadcrumbs,breadcrumbFactory,errorHandling) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createListRepositories())
            ]);

            var PAGESIZE = 15;

            $scope.repositories = {
                items: undefined,
                offset: 0,
                max: PAGESIZE,
                total: undefined
            };

            function clearRepositories() {
                $scope.repositories.items = undefined;
                $scope.repositories.total = undefined;
                $scope.repositories.offset = 0;
            }

            $scope.amShowingInactive = false;
            var amFetchingRepositories = false;

            refetchRepositoriesAtFirstPage();

            $scope.shouldSpin = function() {
                return amFetchingRepositories;
            };

            $scope.goShowInactive = function() {
                $scope.amShowingInactive = true;
                refetchRepositoriesAtFirstPage();
            };

            // ---- LIST MANAGEMENT

            $scope.goSearch = function() {
                clearRepositories();
                refetchRepositoriesAtFirstPage();
            };

            function refetchRepositoriesAtFirstPage() {
                $scope.repositories.offset = 0;
                refetchRepositories();
            }

            function refetchRepositories() {

                amFetchingRepositories = true;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_REPOSITORY,
                        "searchRepositories",
                        [{
                            expression : $scope.searchExpression,
                            expressionType : 'CONTAINS',
                            includeInactive : $scope.amShowingInactive,
                            offset : $scope.repositories.offset,
                            limit : $scope.repositories.max
                        }]
                    ).then(
                    function(result) {
                        $scope.repositories.items = result.items;
                        $scope.repositories.total = result.total;
                        $log.info('found '+result.items.length+' repositories');
                        amFetchingRepositories = false;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            $scope.goAdd = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createAddRepository());
            };

            // ---- EVENTS

            $scope.$watch('repositories.offset', function() {
                refetchRepositories();
            });

        }
    ]
);