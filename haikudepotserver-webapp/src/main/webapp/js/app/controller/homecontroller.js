/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'HomeController',
    [
        '$log','$scope','$rootScope','$q','$location',
        'jsonRpc','constants','userState','messageSource','errorHandling',
        'referenceData','breadcrumbs','breadcrumbFactory','searchMixins',
        'repositoryService',
        function(
            $log,$scope,$rootScope,$q,$location,
            jsonRpc,constants,userState,messageSource,errorHandling,
            referenceData,breadcrumbs,breadcrumbFactory,searchMixins,
            repository) {

            angular.extend(this,searchMixins);

            const PAGESIZE = 15;

            // keys used in the search of the location
            var KEY_OFFSET = 'o';
            var KEY_ARCHITECTURECODE = 'arch';
            var KEY_PKGCATEGORYCODE = 'pkgcat';
            var KEY_REPOSITORYCODES = 'repos';
            var KEY_SEARCHEXPRESSION = 'srchexpr';
            var KEY_VIEWCRITERIATYPECODE = 'viewcrttyp';

            var ViewCriteriaTypes = {
                FEATURED : 'FEATURED',
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
            $scope.repositories = undefined; // pulled in with a promise later...
            $scope.selectedRepositories = undefined;
            $scope.architectures = undefined; // pulled in with a promise later...
            $scope.selectedArchitecture = undefined;
            $scope.pkgCategories = undefined; // pulled in with a promise later...
            $scope.selectedPkgCategory = undefined;
            $scope.viewCriteriaTypeOptions = _.map(
                [
                    ViewCriteriaTypes.FEATURED,
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

            $scope.selectedViewCriteriaTypeOption = undefined; // setup with a function a bit further down...

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
            };

            // if the search criteria has hit the name, but the name is not being displayed because
            // there is a title shown then it makes sense to show the name.

            $scope.shouldExplicitlyShowName = function(pkg) {
                return $scope.lastRefetchPkgsSearchExpression &&
                    $scope.lastRefetchPkgsSearchExpression.length &&
                    pkg.versions[0].title && pkg.versions[0].title.length &&
                    -1 == searchMixins.nextMatchSearchExpression(
                        pkg.versions[0].title.toLowerCase(),0,
                        $scope.lastRefetchPkgsSearchExpression,'CONTAINS').offset &&
                    -1 != searchMixins.nextMatchSearchExpression(
                        pkg.name,0,
                        $scope.lastRefetchPkgsSearchExpression,'CONTAINS').offset;
            };

            $scope.shouldShowDerivedRating = function(pkg) {
                return angular.isNumber(pkg.derivedRating);
            };

            // pagination
            $scope.pkgs = {
                items : undefined,
                offset : $location.search()[KEY_OFFSET] ? parseInt($location.search()[KEY_OFFSET],10) : 0,
                max : PAGESIZE,
                total : undefined
            };

            function resetSelectedRepositories() {

                if(!$scope.repositories || !$scope.repositories.length) {
                    throw Error('the repositories should have been populated');
                }

                var repositoryCodesStr = $location.search()[KEY_REPOSITORYCODES];

                if(repositoryCodesStr && repositoryCodesStr.length) {
                    $scope.selectedRepositories = _.map(
                        _.filter(
                            _.map(repositoryCodesStr.split(','),function(c) { return c.trim(); }),
                            function(c) {
                                return c.length;
                            }
                        ),
                        function(c) {
                            var repository = _.findWhere($scope.repositories, { code : c });

                            if(!repository) {
                                throw new Error('unable to find the repository for; ' + c);
                            }

                            return repository;
                        }
                    );
                }

                if(!$scope.selectedRepositories || !$scope.selectedRepositories.length) {
                    $scope.selectedRepositories = _.filter(
                        $scope.repositories,
                        function(r) { return r.code == constants.REPOSITORY_CODE_DEFAULT; }
                    );
                }

                if(!$scope.selectedRepositories || !$scope.selectedRepositories.length) {
                    throw Error('unable to ascertain the selected repositories');
                }

            }

            function resetSelectedArchitecture() {

                if(!$scope.architectures || !$scope.architectures.length) {
                    throw Error('the architectures should have been populated');
                }

                if($location.search()[KEY_ARCHITECTURECODE]) {
                    $scope.selectedArchitecture = _.findWhere(
                        $scope.architectures,
                        { code : $location.search()[KEY_ARCHITECTURECODE] });
                }

                if(!$scope.selectedArchitecture) {
                    $scope.selectedArchitecture = _.findWhere(
                        $scope.architectures,
                        { code : constants.ARCHITECTURE_CODE_DEFAULT });
                }

                if(!$scope.selectedArchitecture) {
                    $scope.selectedArchitecture = $scope.architectures[0];
                }
            }

            function resetSelectedPkgCategory() {
                if(!$scope.pkgCategories || !$scope.pkgCategories.length) {
                    throw Error('the categories should have been downloaded');
                }

                if($location.search()[KEY_PKGCATEGORYCODE]) {
                    $scope.selectedPkgCategory = _.findWhere($scope.pkgCategories, { code : $location.search()[KEY_PKGCATEGORYCODE] });
                }

                if(!$scope.selectedPkgCategory) {
                    $scope.selectedPkgCategory = $scope.pkgCategories[0];
                }
            }

            function resetSelectedViewCriteriaTypeOption() {
                $scope.selectedViewCriteriaTypeOption = _.findWhere(
                    $scope.viewCriteriaTypeOptions,
                    {
                        code : $location.search()[KEY_VIEWCRITERIATYPECODE] ? $location.search()[KEY_VIEWCRITERIATYPECODE] : ViewCriteriaTypes.FEATURED
                    }
                );
            }

            resetSelectedViewCriteriaTypeOption();

            /**
             * <p>This function will reset the page and return it to its default state.</p>
             */

            function reset() {
                $scope.searchExpression = '';
                $scope.lastRefetchPkgsSearchExpression = '';
                resetSelectedRepositories();
                resetSelectedArchitecture();
                resetSelectedPkgCategory();
                resetSelectedViewCriteriaTypeOption();
                clearPkgs();
                refetchPkgsAtFirstPage();
            }

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

                // fetch the repositories
                function(chain) {
                    repository.getRepositories().then(
                        function(data) {
                            $scope.repositories = data;
                            resetSelectedRepositories();
                            fnChain(chain); // carry on...
                        },
                        function() { // error logged already
                            errorHandling.navigateToError();
                        }
                    );
                },

                // fetch the architectures
                function(chain) {
                    referenceData.architectures().then(
                        function(data) {
                            $scope.architectures = data;
                            resetSelectedArchitecture();
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

                            resetSelectedPkgCategory();
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
                        if(undefined!=oldValue && null!=oldValue) { // already initialized elsewhere
                            $log.debug('offset -> refetching pkgs');
                            refetchPkgs();
                        }
                    });

                    $scope.$watch('selectedPkgCategory', function(newValue, oldValue) {
                        if(!!oldValue && oldValue.code != newValue.code) { // already initialized elsewhere
                            var option = $scope.selectedViewCriteriaTypeOption;

                            if (option && option.code == ViewCriteriaTypes.CATEGORIES) {
                                $log.debug('selectedPkgCategory -> refetching pkgs');
                                refetchPkgsAtFirstPage();
                            }
                        }
                    });

                    // this gets hit when somebody chooses an architecture such as x86, x86_64 etc...

                    $scope.$watch('selectedArchitecture', function(newValue, oldValue) {
                        if(!!oldValue && newValue != oldValue) { // already initialized elsewhere
                            $log.debug('selectedArchitecture -> refetching pkgs');
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
                                case ViewCriteriaTypes.FEATURED:
                                case ViewCriteriaTypes.ALL:
                                    $log.debug('selectedViewCriteriaTypeOption -> refetching pkgs');
                                    refetchPkgsAtFirstPage();
                                    break;

                                case ViewCriteriaTypes.CATEGORIES:
                                    if(!$scope.pkgCategories) {
                                        refetchPkgCategories();
                                    }
                                    $log.debug('selectedViewCriteriaTypeOption -> refetching pkgs');
                                    refetchPkgsAtFirstPage();
                                    break;

                                default:
                                    throw new Error('unknown view criteria type option; ' + newValue.code);

                            }
                        }
                    });

                    fnChain(chain);
                }

            ]);

            // ---- VIEW PKG + VERSION

            /**
             * <p>This is used to provide an 'open in tab' link.</p>
             */

            $scope.viewPkgPath = function(pkg) {
                return breadcrumbFactory.toFullPath(
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg(pkg)
                );
            };

            $scope.goViewPkg = function(pkg,event) {
                if(0 == event.button) { // left button only.
                    event.preventDefault();

                    breadcrumbs.pushAndNavigate(
                        breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg(pkg)
                    );
                }
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

                if($scope.selectedArchitecture && $scope.selectedRepositories) {

                    // store the parameters for reproducing the page.

                    if (0 != $scope.pkgs.offset) {
                        $location.search(KEY_OFFSET, '' + $scope.pkgs.offset);
                    }
                    else {
                        $location.search(KEY_OFFSET, null);
                    }

                    $location.search(
                        KEY_REPOSITORYCODES,
                        _.map(
                            $scope.selectedRepositories,
                            function(r) {
                                return r.code;
                            }
                        ).join(',')
                    );
                    $location.search(KEY_ARCHITECTURECODE,$scope.selectedArchitecture.code);
                    $location.search(KEY_SEARCHEXPRESSION,$scope.searchExpression);
                    $location.search(KEY_VIEWCRITERIATYPECODE,$scope.selectedViewCriteriaTypeOption.code);

                    if(ViewCriteriaTypes.CATEGORIES == $scope.selectedViewCriteriaTypeOption.code) {
                        $location.search(KEY_PKGCATEGORYCODE, $scope.selectedPkgCategory.code);
                    }
                    else {
                        $location.search(KEY_PKGCATEGORYCODE, null);
                    }

                    if($scope.searchExpression && $scope.searchExpression.length) {
                        $location.search(KEY_SEARCHEXPRESSION,$scope.searchExpression);
                    }
                    else {
                        $location.search(KEY_SEARCHEXPRESSION,null);
                    }

                    // copy those search values into the breadcrumb
                    var item = breadcrumbs.peek();

                    if(!item) {
                        throw Error('expected the home breadcrumb to be present');
                    }

                    item.search = $location.search();

                    amFetchingPkgs = true;

                    //$log.info('repos; ' + $scope.selectedRepositories);

                    var req = {
                        repositoryCodes : _.map($scope.selectedRepositories, function(r) {
                            return r.code;
                        }),
                        architectureCodes: [
                            'any',
                            $scope.selectedArchitecture.code
                        ],
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

                        case ViewCriteriaTypes.FEATURED:
                            req.sortOrdering = 'PROMINENCE';
                            break;

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

                        default:
                            throw new Error('unexpected view criteria type option; ' + $scope.selectedViewCriteriaTypeOption.code);

                    }

                    $scope.lastRefetchPkgsSearchExpression = $scope.searchExpression;

                    jsonRpc.call(constants.ENDPOINT_API_V1_PKG, "searchPkgs", [req]).then(
                        function (result) {

                            // This will either return the title from the package's version or it will return the
                            // name of the package.

                            function derivedTitle(pkg) {
                                if(pkg.versions &&
                                    1==pkg.versions.length &&
                                    pkg.versions[0].title &&
                                    pkg.versions[0].title.length) {
                                    return pkg.versions[0].title;
                                }

                                return pkg.name;
                            }

                            $scope.pkgs.items = result.items;
                            $scope.pkgs.total = result.total;

                            _.each($scope.pkgs.items, function(p) {
                                p.derivedTitle = derivedTitle(p);
                            });

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
                function(event, newValue, oldValue) {
                    if(!!oldValue) {
                        updateViewCriteriaTypeOptionsTitles();
                        updatePkgCategoryTitles();
                        refetchPkgs(); // title may change
                    }
                }
            );

            // this event will fire when somebody clicks on the haiku depot icon at the top left in order to
            // trigger a return to the home page.

            $scope.$on(
                'didResetToHome',
                function() {
                    reset();
                }
            );

        }
    ]
);