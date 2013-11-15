/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$scope','$log','$location','jsonRpc','constants','userState',
        function(
            $scope,$log,$location,jsonRpc,constants,userState) {

            const PAGESIZE = 14;

            // This is just some sample data.  In reality, this material would be obtained from the server and then
            // cached locally.

            $scope.categories = [
                'Category...',
                'Temporary',
                'Example',
                'Test',
                'Cases'
            ];

            $scope.ViewCriteriaTypes = {
                SEARCH : 'SEARCH',
                MOSTVIEWED : 'MOSTVIEWED',
                CATEGORIES : 'CATEGORIES',
                MOSTRECENT : 'MOSTRECENT'
            };

            // default model settings.

            $scope.viewCriteriaType = $scope.ViewCriteriaTypes.MOSTRECENT;
            $scope.selectedCategory = $scope.categories[0];
            $scope.searchExpression = '';
            $scope.lastRefetchPkgsSearchExpression = '';
            $scope.pkgs = undefined;
            $scope.hasMore = undefined;
            $scope.offset = 0;

            refetchPkgsAtFirstPage();

            // ---- WATCHES
            // watch various values and react.

            $scope.$watch('selectedCategory', function(newValue) {
                if(newValue !== $scope.categories[0]) {
                    $scope.goChooseCategory();
                }
            });

            $scope.shouldSpin = function() {
                return undefined == $scope.pkgs;
            }

            // ---- SEARCH CRITERION USER INTERFACE
            // functions to control the user interface; not much generalization here as there will be some specific
            // logic.

            function setViewCriteriaType(type) {
                if($scope.viewCriteriaType != type) {
                    $scope.viewCriteriaType = type;

                    if(type != $scope.ViewCriteriaTypes.SEARCH) {
                        $scope.searchExpression = '';
                    }

                    if(type != $scope.ViewCriteriaTypes.CATEGORIES) {
                        $scope.selectedCategory = $scope.categories[0];
                    }

                    refetchPkgsAtFirstPage();
                }
            }

            $scope.classCategories = function() {
                return $scope.viewCriteriaType == $scope.ViewCriteriaTypes.CATEGORIES ? 'selected' : null;
            }

            $scope.classMostRecent = function() {
                return $scope.viewCriteriaType == $scope.ViewCriteriaTypes.MOSTRECENT ? 'selected' : null;
            }

            $scope.classMostViewed = function() {
                return $scope.viewCriteriaType == $scope.ViewCriteriaTypes.MOSTVIEWED ? 'selected' : null;
            }

            $scope.classSearch = function() {
                return $scope.viewCriteriaType == $scope.ViewCriteriaTypes.SEARCH ? 'selected' : null;
            }

            $scope.goMostRecent = function() {
                setViewCriteriaType($scope.ViewCriteriaTypes.MOSTRECENT);
                return false;
            }

            $scope.goMostViewed = function() {
                setViewCriteriaType($scope.ViewCriteriaTypes.MOSTVIEWED);
                return false;
            }

            $scope.goChooseCategory = function() {
                setViewCriteriaType($scope.ViewCriteriaTypes.CATEGORIES);
                return false;
            }

            $scope.goSearchExpression = function() {
                if($scope.viewCriteriaType == $scope.ViewCriteriaTypes.SEARCH) {
                    if($scope.lastRefetchPkgsSearchExpression != $scope.searchExpression) {
                        refetchPkgsAtFirstPage();
                    }
                }
                else {
                    setViewCriteriaType($scope.ViewCriteriaTypes.SEARCH);
                }

                return false;
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

            // ---- VIEW PKG + VERSION

            $scope.goViewPkg = function(pkg) {
                $location.path('/viewpkg/'+pkg.name+'/latest').search({});
                return false;
            }

            // ---- UPDATE THE RESULTS LOGIC

            function refetchPkgsAtFirstPage() {
                $scope.offset = 0;
                refetchPkgs();
            }

            // this function will pop off to the server and will pull-down the list of packages depending on what the
            // user had selected in the criteria.

            function refetchPkgs() {

                $scope.pkgs = undefined;
                $scope.lastRefetchPkgsSearchExpression = $scope.searchExpression;

                userState.architecture().then(
                    function(architecture) {
                        jsonRpc.call(
                                constants.ENDPOINT_API_V1_PKG,
                                "searchPkgs",
                                [{
                                    expression : $scope.searchExpression,
                                    architectureCode : architecture.code,
                                    expressionType : 'CONTAINS',
                                    offset : $scope.offset,
                                    limit : PAGESIZE
                                }]
                            ).then(
                            function(result) {
                                $scope.pkgs = result.pkgs;
                                $scope.hasMore = result.hasMore;
                                $log.info('found '+result.pkgs.length+' packages');
                            },
                            function(err) {
                                constants.ERRORHANDLING_JSONRPC(err,$location,$log);
                            }
                        );
                    },
                    function() {
                        $log.error('a problem has arisen getting the architecture');
                        $location.path("/error").search({});
                    }
                );


            }
        }
    ]
);