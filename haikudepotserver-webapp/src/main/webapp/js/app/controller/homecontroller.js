/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$log','$scope','$rootScope','$q','$location',
        'jsonRpc','constants','userState','messageSource','errorHandling',
        'referenceData','breadcrumbs',
        function(
            $log,$scope,$rootScope,$q,$location,
            jsonRpc,constants,userState,messageSource,errorHandling,
            referenceData,breadcrumbs) {

            const PAGESIZE = 50;

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
            $scope.architectures = undefined; // pulled in with a promise.
            $scope.selectedArchitecture = undefined;
            $scope.pkgCategories = undefined;
            $scope.selectedPkgCategory = undefined;
            $scope.viewCriteriaTypeOptions = _.map(
                [
                    ViewCriteriaTypes.ALL,
                    ViewCriteriaTypes.SEARCH,
                    ViewCriteriaTypes.CATEGORIES,
                    ViewCriteriaTypes.MOSTRECENT,
                    ViewCriteriaTypes.MOSTVIEWED
                ],
                function(k) {
                    return {
                        code : k,
                        titleKey : 'home.viewCriteriaType.' + k.toLowerCase(),
                        title : 'home.viewCriteriaType.' + k.toLowerCase()
                    };
                }
            );
            $scope.selectedViewCriteriaTypeOption = _.find(
                $scope.viewCriteriaTypeOptions,
                function(o) {
                    return o.code == ViewCriteriaTypes.ALL;
                }
            );

            // pagination
            $scope.pkgs = undefined;
            $scope.hasMore = undefined;
            $scope.offset = 0;

            // update the localized names on the options

            function updateViewCriteriaTypeOptionsTitles() {
                _.each(
                    $scope.viewCriteriaTypeOptions,
                    function(o) {
                        messageSource
                            .get(
                                userState.naturalLanguageCode(),
                                o.titleKey)
                            .then(
                            function(value) {
                                o.title = value;
                            },
                            function() { // error already logged
                                o.title = '???';
                            }
                        );
                    }
                );
            }

            updateViewCriteriaTypeOptionsTitles();

            $rootScope.$on(
                "naturalLanguageChange",
                function() {
                    updateViewCriteriaTypeOptionsTitles();
                }
            );

            $scope.$watch('selectedPkgCategory', function() {
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

                        case ViewCriteriaTypes.MOSTRECENT:
                        case ViewCriteriaTypes.MOSTVIEWED:
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

                    }
                }
            });

            $scope.shouldSpin = function() {
                return amFetchingPkgs || !$scope.architectures;
            };

            breadcrumbs.mergeCompleteStack([ breadcrumbs.createHome() ]);

            // ---- ARCHITECTURES

            function refetchArchitectures() {
                referenceData.architectures().then(
                    function(data) {
                        $scope.architectures = data;
                        $scope.selectedArchitecture = $scope.architectures[0];

                        // it would not have been possible to fetch the packages' list without having the architecture
                        // defined.  For this reason, we should now attempt to trigger the fetch of the architectures.

                        if(undefined==$scope.pkgs) {
                            refetchPkgs();
                        }
                    },
                    function() { // error logged already
                        errorHandling.navigateToError();
                    }
                );
            }

            refetchArchitectures();

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
                        errorHandling.navigateToError();
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
            };

            $scope.goNextPage = function() {
                if($scope.hasMore) {
                    $scope.offset += PAGESIZE;
                    refetchPkgs();
                }

                return false;
            };

            // ---- VIEW PKG + VERSION

            $scope.goViewPkg = function(pkg) {
                $location.path('/pkg/'+pkg.name+'/latest/'+$scope.selectedArchitecture.code);
                return false;
            };

            // ---- UPDATE THE RESULTS LOGIC

            $scope.goSearch = function() {
                if($scope.lastRefetchPkgsSearchExpression != $scope.searchExpression) {
                    $scope.pkgs = undefined;
                    refetchPkgsAtFirstPage();
                }
            };

            function refetchPkgsAtFirstPage() {
                $scope.offset = 0;
                refetchPkgs();
            }

            // this function will pop off to the server and will pull-down the list of packages depending on what the
            // user had selected in the criteria.

            function refetchPkgs() {

                // it is not possible to fetch packages if there is no architecture selected.  This should be OK to
                // stop via a conditional because when the architecture is selected (fetched itself) then it will
                // automatically attempt this refetch again.

                if($scope.selectedArchitecture) {

                    amFetchingPkgs = true;
                    $scope.pkgs = undefined;
                    $scope.hasMore = false;

                    var req = {
                        architectureCode: $scope.selectedArchitecture.code,
                        offset: $scope.offset,
                        limit: PAGESIZE
                    };

                    switch ($scope.selectedViewCriteriaTypeOption.code) {

                        case ViewCriteriaTypes.ALL:
                            break;

                        case ViewCriteriaTypes.SEARCH:
                            req.expression = $scope.searchExpression;
                            req.expressionType = 'CONTAINS';
                            break;

                        case ViewCriteriaTypes.CATEGORIES:
                            if (!$scope.selectedPkgCategory) {
                                amFetchingPkgs = false;
                                return;
                            }
                            req.pkgCategoryCode = $scope.selectedPkgCategory.code;
                            break;

                        case ViewCriteriaTypes.MOSTRECENT:
                            req.daysSinceLatestVersion = constants.RECENT_DAYS;
                            req.sortOrdering = 'VERSIONCREATETIMESTAMP';
                            break;

                        case ViewCriteriaTypes.MOSTVIEWED:
                            req.daysSinceLatestVersion = constants.RECENT_DAYS;
                            req.sortOrdering = 'VERSIONVIEWCOUNTER';
                            break;

                    }

                    $scope.lastRefetchPkgsSearchExpression = $scope.searchExpression;

                    jsonRpc.call(constants.ENDPOINT_API_V1_PKG, "searchPkgs", [req]).then(
                        function (result) {
                            $scope.pkgs = result.items;
                            $scope.hasMore = result.hasMore;
                            $log.info('found ' + result.items.length + ' packages');
                            amFetchingPkgs = false;
                        },
                        function (err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                }

            }
        }
    ]
);