/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ListRepositoriesController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants',
        'breadcrumbs','errorHandling',
        function(
            $scope,$log,$location,
            jsonRpc,constants,
            breadcrumbs,errorHandling) {

            $scope.breadcrumbItems = [
                breadcrumbs.createMore(),
                breadcrumbs.createListRepositories()
            ];

            const PAGESIZE = 14;

            $scope.repositories = undefined;
            $scope.hasMore = undefined;
            $scope.offset = 0;

            refetchRepositoriesAtFirstPage();

            var amFetchingRepositories = false;

            $scope.shouldSpin = function() {
                return amFetchingRepositories;
            }

            // ---- PAGINATION

            $scope.goSearch = function() {
                    $scope.repositories = undefined;
                refetchRepositoriesAtFirstPage();
            }

            function refetchRepositoriesAtFirstPage() {
                $scope.offset = 0;
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
                            offset : $scope.offset,
                            limit : PAGESIZE
                        }]
                    ).then(
                    function(result) {
                        $scope.repositories = result.items;
                        $scope.hasMore = result.hasMore;
                        $log.info('found '+result.items.length+' repositories');
                        amFetchingRepositories = false;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // ---- PAGINATION

            $scope.goPreviousPage = function() {
                if($scope.offset > 0) {
                    $scope.offset -= PAGESIZE;
                    refetchPkgs();
                }

                return false;
            }

            $scope.goNextPage = function() {
                if($scope.hasMore) {
                    $scope.offset += PAGESIZE;
                    refetchPkgs();
                }

                return false;
            }

            $scope.classPreviousPage = function() {
                return $scope.offset > 0 ? [] : ['disabled'];
            }

            $scope.classNextPage = function() {
                return $scope.hasMore ? [] : ['disabled'];
            }


        }
    ]
);