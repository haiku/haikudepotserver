/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$scope','$q','$log','$location',
        'jsonRpc','constants','userState','architectures','messageSource','errorHandling',
        function(
            $scope,$q,$log,$location,
            jsonRpc,constants,userState,architectures,messageSource,errorHandling) {

            const PAGESIZE = 14;

            var ViewCriteriaTypes = {
                ALL : 'ALL',
                SEARCH : 'SEARCH',
                MOSTVIEWED : 'MOSTVIEWED',
                CATEGORIES : 'CATEGORIES',
                MOSTRECENT : 'MOSTRECENT'
            };

            // default model settings.

            var amFetchingPkgs = false;
            $scope.selectedViewCriteriaTypeOption = { code : ViewCriteriaTypes.ALL };
            $scope.searchExpression = '';
            $scope.lastRefetchPkgsSearchExpression = '';
            $scope.architectures = architectures;
            $scope.selectedArchitecture = $scope.architectures[0];

            $scope.pkgs = undefined;
            $scope.hasMore = undefined;
            $scope.offset = 0;

            // get the localized view criteria type names.

            function deriveViewCriteriaTypeOptions() {

                var promises = {}

                for(var key in ViewCriteriaTypes) {
                    if(ViewCriteriaTypes.hasOwnProperty(key)) {
                        promises[key] = messageSource.get('home.viewCriteriaType.' + key.toLowerCase());
                    }
                }

                $q.all(promises).then(

                    function(result) {

                        var options = [];

                        _.map(result, function(v,k) {
                            options.push({ code: k, name: v });
                        });

                        options = _.sortBy(options, function(o) {
                            return o.name;
                        })

                        $scope.selectedViewCriteriaTypeOption = _.find(options, function(o) {
                            return o.code == ViewCriteriaTypes.ALL;
                        });

                        $scope.viewCriteriaTypeOptions = options;
                        refetchPkgsAtFirstPage();

                    },
                    function() {
                        $log.error('a problem has arisen getting the view criteria types');
                        $location.path("/error").search({});
                    }
                );

            }

            deriveViewCriteriaTypeOptions();

            // this gets hit when somebody chooses an architecture such as x86, x86_64 etc...

            $scope.$watch('selectedArchitecture', function() {

                if(undefined != $scope.pkgs) {
                    refetchPkgsAtFirstPage();
                }

            });

            // this gets hit when the user chooses between the various options such as "all", "search" etc...

            $scope.$watch('selectedViewCriteriaTypeOption', function(newValue) {
                if(ViewCriteriaTypes.SEARCH != newValue.code) {
                    $scope.searchExpression = '';
                    $scope.lastRefetchPkgsSearchExpression = '';
                }

                $scope.pkgs = undefined;

                switch(newValue.code) {

                    case ViewCriteriaTypes.ALL:
                        refetchPkgsAtFirstPage();
                        break;

                    case ViewCriteriaTypes.SEARCH:
                        break;

                    case ViewCriteriaTypes.CATEGORIES: // TODO
                    case ViewCriteriaTypes.MOSTRECENT: // TODO
                    case ViewCriteriaTypes.MOSTVIEWED: // TODO
                        break;

                }
            });

            $scope.shouldSpin = function() {
                return amFetchingPkgs;
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
                $location.path('/viewpkg/'+pkg.name+'/latest/'+$scope.selectedArchitecture.code);
                return false;
            }

            // ---- UPDATE THE RESULTS LOGIC

            $scope.goSearch = function() {
                if($scope.lastRefetchPkgsSearchExpression != $scope.searchExpression) {
                    $scope.pkgs = undefined;
                    refetchPkgsAtFirstPage();
                }
            }

            function refetchPkgsAtFirstPage() {
                $scope.offset = 0;
                refetchPkgs();
            }

            // this function will pop off to the server and will pull-down the list of packages depending on what the
            // user had selected in the criteria.

            function refetchPkgs() {

                amFetchingPkgs = true;
                $scope.lastRefetchPkgsSearchExpression = $scope.searchExpression;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "searchPkgs",
                        [{
                            expression : $scope.searchExpression,
                            architectureCode : $scope.selectedArchitecture.code,
                            expressionType : 'CONTAINS',
                            offset : $scope.offset,
                            limit : PAGESIZE
                        }]
                    ).then(
                    function(result) {
                        $scope.pkgs = result.pkgs;
                        $scope.hasMore = result.hasMore;
                        $log.info('found '+result.pkgs.length+' packages');
                        amFetchingPkgs = false;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );


            }
        }
    ]
);