/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$log','$scope','$rootScope','$q','$location',
        'jsonRpc','constants','userState','messageSource','errorHandling',
        'referenceData','breadcrumbs','breadcrumbFactory','searchMixins',
        function(
            $log,$scope,$rootScope,$q,$location,
            jsonRpc,constants,userState,messageSource,errorHandling,
            referenceData,breadcrumbs,breadcrumbFactory,searchMixins) {

            angular.extend(this,searchMixins);

            const PAGESIZE = 15;

            // keys used in the search of the location
            var KEY_OFFSET = 'o';
            var KEY_ARCHITECTURECODE = 'arch';
            var KEY_PKGCATEGORYCODE = 'pkgcat';
            var KEY_SEARCHEXPRESSION = 'srchexpr';
            var KEY_VIEWCRITERIATYPECODE = 'viewcrttyp';

            var ViewCriteriaTypes = {
                ALL : 'ALL',
                MOSTVIEWED : 'MOSTVIEWED',
                CATEGORIES : 'CATEGORIES',
                MOSTRECENT : 'MOSTRECENT'
            };

            // default model settings.

            var amFetchingPkgs = false;

            $scope.selectedViewCriteriaTypeOption = undefined;
            $scope.searchExpression = $location.search()[KEY_SEARCHEXPRESSION] ? $location.search()[KEY_SEARCHEXPRESSION] : '';
            $scope.lastRefetchPkgsSearchExpression = '';
            $scope.architectures = undefined; // pulled in with a promise later...
            $scope.selectedArchitecture = undefined;
            $scope.pkgCategories = undefined; // pulled in with a promise later...
            $scope.selectedPkgCategory = undefined;
            $scope.viewCriteriaTypeOptions = _.map(
                [
                    ViewCriteriaTypes.ALL,
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

            $scope.selectedViewCriteriaTypeOption = _.findWhere(
                $scope.viewCriteriaTypeOptions,
                {
                    code : $location.search()[KEY_VIEWCRITERIATYPECODE] ? $location.search()[KEY_VIEWCRITERIATYPECODE] : ViewCriteriaTypes.ALL
                }
            );

            // if the search criteria has an expression that has not hit the package name, but has
            // hit the summary then we need to show the summary as well as the package name.

            $scope.shouldShowSummary = function(pkg) {
                return $scope.lastRefetchPkgsSearchExpression &&
                    $scope.lastRefetchPkgsSearchExpression.length &&
                    pkg.versions[0].summary &&
                    pkg.versions[0].summary.length &&
                    -1 == searchMixins.nextMatchSearchExpression(
                        pkg.name,0,
                        $scope.lastRefetchPkgsSearchExpression,'CONTAINS').offset &&
                    -1 != searchMixins.nextMatchSearchExpression(
                        pkg.versions[0].summary.toLowerCase(),0,
                        $scope.lastRefetchPkgsSearchExpression,'CONTAINS').offset;
            }

            $scope.shouldShowDerivedRating = function(pkg) {
                return angular.isNumber(pkg.derivedRating);
            }

            // pagination
            $scope.pkgs = {
                items : undefined,
                offset : $location.search()[KEY_OFFSET] ? parseInt($location.search()[KEY_OFFSET],10) : 0,
                max : PAGESIZE,
                total : undefined
            };

            function clearPkgs() {
                $scope.pkgs.items = undefined;
                $scope.pkgs.total = undefined;
            }

            $scope.shouldSpin = function() {
                return amFetchingPkgs || !$scope.architectures;
            };

            breadcrumbs.mergeCompleteStack([ breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createHome()) ]);

            // ---- LOCALIZATION

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

            // called from within the function chain below.
            function updatePkgCategoryTitles() {
                _.each(
                    $scope.pkgCategories,
                    function(c) {
                        messageSource.get(
                            userState.naturalLanguageCode(),
                            c.titleKey
                        )
                            .then(
                            function(value) {
                                c.title = value;
                            },
                            function() { // error already logged
                                c.title = '???';
                            }
                        );
                    }
                );
            }

            // ---- INITIALIZATION FUNCTION CHAIN

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            fnChain([

                // fetch the architectures
                function(chain) {
                    referenceData.architectures().then(
                        function(data) {
                            $scope.architectures = data;

                            if($location.search()[KEY_ARCHITECTURECODE]) {
                                $scope.selectedArchitecture = _.findWhere(data,{ code : $location.search()[KEY_ARCHITECTURECODE] });
                            }

                            if(!$scope.selectedArchitecture) {
                                $scope.selectedArchitecture = $scope.architectures[0];
                            }

                            fnChain(chain); // carry on...
                        },
                        function() { // error logged already
                            errorHandling.navigateToError();
                        }
                    );
                },

                // fetch the pkg categories
                function(chain) {

                    referenceData.pkgCategories().then(
                        function(data) {

                            $scope.pkgCategories = _.map(
                                data,
                                function(c) {
                                    return {
                                        code : c.code,
                                        titleKey : 'pkgCategory.' + c.code.toLowerCase() + '.title',
                                        title : c.name // temporary.
                                    }
                                }
                            );

                            if($location.search()[KEY_PKGCATEGORYCODE]) {
                                $scope.selectedPkgCategory = _.findWhere($scope.pkgCategories, { code : $location.search()[KEY_PKGCATEGORYCODE] });
                            }

                            if(!$scope.selectedPkgCategory) {
                                $scope.selectedPkgCategory = $scope.pkgCategories[0];
                            }

                            updatePkgCategoryTitles();

                            fnChain(chain); // carry on...
                        },
                        function() {
                            $log.error('unable to obtain the list of pkg categories');
                            errorHandling.navigateToError();
                        }
                    );
                },

                // make an initial fetch of packages
                function(chain) {
                    refetchPkgs();
                    fnChain(chain); // carry on...
                },

                // register some event handlers that will then prompt re-loading of the package list
                // as necessary.

                function(chain) {

                    $scope.$watch('pkgs.offset', function(newValue, oldValue) {
                        refetchPkgs();
                    });

                    $scope.$watch('selectedPkgCategory', function(newValue, oldValue) {
                        var option = $scope.selectedViewCriteriaTypeOption;

                        if(option && option.code == ViewCriteriaTypes.CATEGORIES) {
                            refetchPkgsAtFirstPage();
                        }
                    });

                    // this gets hit when somebody chooses an architecture such as x86, x86_64 etc...

                    $scope.$watch('selectedArchitecture', function(newValue, oldValue) {

                        if(undefined != $scope.pkgs.items) {
                            refetchPkgsAtFirstPage();
                        }

                    });

                    // this gets hit when the user chooses between the various options such as "all", "search" etc...

                    $scope.$watch('selectedViewCriteriaTypeOption', function(newValue, oldValue) {
                        clearPkgs();

                        if(newValue && (!oldValue || oldValue.code != newValue.code)) { // will initially be undefined.

                            switch(newValue.code) {

                                case ViewCriteriaTypes.MOSTRECENT:
                                case ViewCriteriaTypes.MOSTVIEWED:
                                case ViewCriteriaTypes.ALL:
                                    refetchPkgsAtFirstPage();
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

                    fnChain(chain);
                }

            ]);

            // ---- VIEW PKG + VERSION

            $scope.goViewPkg = function(pkg) {

                breadcrumbs.pushAndNavigate(
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg(pkg)
                );

                return false;
            };

            // ---- UPDATE THE RESULTS LOGIC

            $scope.goSearch = function() {
                if($scope.lastRefetchPkgsSearchExpression != $scope.searchExpression) {
                    clearPkgs();
                    refetchPkgsAtFirstPage();
                }
            };

            function refetchPkgsAtFirstPage() {
                $scope.pkgs.offset = 0;
                refetchPkgs();
            }

            // this function will pop off to the server and will pull-down the list of packages depending on what the
            // user had selected in the criteria.

            function refetchPkgs() {

                // it is not possible to fetch packages if there is no architecture selected.  This should be OK to
                // stop via a conditional because when the architecture is selected (fetched itself) then it will
                // automatically attempt this refetch again.

                if($scope.selectedArchitecture) {

                    // store the parameters for reproducing the page.
                    $location.search(KEY_OFFSET,''+$scope.pkgs.offset);
                    $location.search(KEY_ARCHITECTURECODE,$scope.selectedArchitecture.code);
                    $location.search(KEY_PKGCATEGORYCODE,$scope.selectedPkgCategory.code);
                    $location.search(KEY_SEARCHEXPRESSION,$scope.searchExpression);
                    $location.search(KEY_VIEWCRITERIATYPECODE,$scope.selectedViewCriteriaTypeOption.code);
                    breadcrumbs.peek().search = $location.search();

                    amFetchingPkgs = true;

                    var req = {
                        architectureCode: $scope.selectedArchitecture.code,
                        naturalLanguageCode : userState.naturalLanguageCode(),
                        offset: $scope.pkgs.offset,
                        limit: PAGESIZE
                    };

                    var preparedSearchExpression = $scope.searchExpression ? $scope.searchExpression.trim() : null;

                    if(preparedSearchExpression && preparedSearchExpression.length) {
                        req.expression = preparedSearchExpression;
                        req.expressionType = 'CONTAINS';
                    }

                    switch ($scope.selectedViewCriteriaTypeOption.code) {

                        case ViewCriteriaTypes.ALL:
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
                            $scope.pkgs.items = result.items;
                            $scope.pkgs.total = result.total;
                            $log.info('found ' + result.items.length + ' packages from a total of '+result.total);
                            amFetchingPkgs = false;
                        },
                        function (err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                }

            }

            // ---- SUNDRY EVENT HANDLING

            $scope.$on(
                "naturalLanguageChange",
                function() {
                    updateViewCriteriaTypeOptionsTitles();
                    updatePkgCategoryTitles();
                }
            );

        }
    ]
);