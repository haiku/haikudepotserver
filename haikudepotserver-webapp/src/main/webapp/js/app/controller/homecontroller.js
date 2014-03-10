/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$log','$scope','$q','$log','$location',
        'jsonRpc','constants','userState','architectures','messageSource','errorHandling',
        'referenceData',
        function(
            $log,$scope,$q,$log,$location,
            jsonRpc,constants,userState,architectures,messageSource,errorHandling,
            referenceData) {

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

            $scope.selectedViewCriteriaTypeOption = undefined;
            $scope.searchExpression = '';
            $scope.lastRefetchPkgsSearchExpression = '';
            $scope.architectures = architectures;
            $scope.selectedArchitecture = $scope.architectures[0];
            $scope.pkgCategories = undefined;
            $scope.selectedPkgCategory = undefined;

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

                    },
                    function() {
                        $log.error('a problem has arisen getting the view criteria types');
                        $location.path("/error").search({});
                    }
                );

            }

            deriveViewCriteriaTypeOptions();

            $scope.$watch('selectedPkgCategory', function(newValue) {
                var option = $scope.selectedViewCriteriaTypeOption;

                if(option && option.code == ViewCriteriaTypes.CATEGORIES) {
                    refetchPkgsAtFirstPage();
                }
            });

            // this gets hit when somebody chooses an architecture such as x86, x86_64 etc...

            $scope.$watch('selectedArchitecture', function() {

                if(undefined != $scope.pkgs) {
                    refetchPkgsAtFirstPage();
                }

            });

            // this gets hit when the user chooses between the various options such as "all", "search" etc...

            $scope.$watch('selectedViewCriteriaTypeOption', function(newValue) {
                $scope.pkgs = undefined;

                if(newValue) { // will initially be undefined.

                    if(ViewCriteriaTypes.SEARCH != newValue.code) {
                        $scope.searchExpression = '';
                        $scope.lastRefetchPkgsSearchExpression = '';
                    }

                    switch(newValue.code) {

                        case ViewCriteriaTypes.ALL:
                            refetchPkgsAtFirstPage();
                            break;

                        case ViewCriteriaTypes.SEARCH:
                            break;

                        case ViewCriteriaTypes.CATEGORIES:
                            if(!$scope.pkgCategories) {
                                refetchPkgCategories();
                            }
                            refetchPkgsAtFirstPage();
                            break;

                        case ViewCriteriaTypes.MOSTRECENT: // TODO
                        case ViewCriteriaTypes.MOSTVIEWED: // TODO
                            break;

                    }
                }
            });

            $scope.shouldSpin = function() {
                return amFetchingPkgs;
            }

            // ---- CATEGORIES

            // if the user is searching by category; view only those packages in a given category then they will need
            // to be presented with that list of categories.  This function will pull those categories into this page
            // and setup the default selection.

            function refetchPkgCategories() {
                $scope.pkgCategories = undefined;
                $scope.selectedPkgCategory = undefined;

                referenceData.pkgCategories().then(
                    function(data) {
                        $scope.pkgCategories = data;
                        $scope.selectedPkgCategory = data[0]; // will trigger refetch of packages if required.
                    },
                    function() {
                        $log.error('unable to obtain the list of pkg categories');
                        $location.path("/error").search({});
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
                $scope.pkgs = undefined;
                $scope.hasMore = false;

                var req = {
                    architectureCode : $scope.selectedArchitecture.code,
                    offset : $scope.offset,
                    limit : PAGESIZE
                }

                switch($scope.selectedViewCriteriaTypeOption.code) {

                    case ViewCriteriaTypes.ALL:
                        break;

                    case ViewCriteriaTypes.SEARCH:
                        req.expression = $scope.searchExpression;
                        req.expressionType = 'CONTAINS';
                        break;

                    case ViewCriteriaTypes.CATEGORIES:
                        if(!$scope.selectedPkgCategory) {
                            amFetchingPkgs = false;
                            return;
                        }
                        req.pkgCategoryCodes = [ $scope.selectedPkgCategory.code ];
                        break;

                    case ViewCriteriaTypes.MOSTRECENT: // TODO
                    case ViewCriteriaTypes.MOSTVIEWED: // TODO
                        break;

                }

                $scope.lastRefetchPkgsSearchExpression = $scope.searchExpression;

                jsonRpc.call(constants.ENDPOINT_API_V1_PKG,"searchPkgs",[req]).then(
                    function(result) {
                        $scope.pkgs = result.items;
                        $scope.hasMore = result.hasMore;
                        $log.info('found '+result.items.length+' packages');
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