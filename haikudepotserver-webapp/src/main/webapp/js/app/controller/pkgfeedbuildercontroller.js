/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller allows the user to choose aspects of a package feed and to be able to
 * configure a URL which can be used with a feed reader.</p>
 */

angular.module('haikudepotserver').controller(
    'PkgFeedBuilderController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','messageSource','referenceData',
        'breadcrumbs','breadcrumbFactory','errorHandling','userState',
        function(
            $scope,$log,$location,
            jsonRpc,constants,messageSource,referenceData,
            breadcrumbs,breadcrumbFactory,errorHandling,userState) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createPkgFeedBuilder())
            ]);

            $scope.pkgChooserName = '';
            $scope.pkgNamePattern = ('' + constants.PATTERN_PKG_NAME).replace(/^\//,'').replace(/\/$/,'');
            $scope.pkgNamePlaceholder = undefined;
            $scope.feedUrl = undefined;
            $scope.limits = [5,10,25,50,75,100];
            $scope.feedSettings = {
                pkgs: [],
                limit: 25,
                supplierTypes: undefined
            };

            var amBuilding = false;

            $scope.shouldSpin = function() {
                return !$scope.feedSettings.supplierTypes || amBuilding;
            };

            messageSource.get(
                userState.naturalLanguageCode(),
                'pkgFeedBuilder.pkgChooserName.placeholder')
                .then(
                function(text) { $scope.pkgNamePlaceholder = text; }
            );

            function refreshFeedSupplierTypes() {
                referenceData.feedSupplierTypes().then(
                    function(items) {

                        $scope.feedSettings.supplierTypes = _.map(items, function(item) {
                            return {
                                code : item.code,
                                title : '...',
                                selected : true
                            };
                        });

                        _.each(
                            $scope.feedSettings.supplierTypes,
                            function(item) {
                                messageSource.get(
                                    userState.naturalLanguageCode(),
                                        'feed.source.'+item.code.toLowerCase()+'.title'
                                ).then(
                                    function(title) {
                                        item.title = title;
                                    },
                                    function() {
                                        item.title = '???';
                                    }
                                );
                            }
                        );

                    },
                    function() {
                        errorHandling.navigateToError();
                    }
                )
            }

            refreshFeedSupplierTypes();

            /**
             * <p>This function will build the feed URL; it calls back to the server to do this in order to
             * avoid coding too much of the URL building logic in the page.</p>
             */

            function build() {

                amBuilding = true;

                var pkgNames = null;
                var supplierTypeCodes = _.pluck(
                    _.where($scope.feedSettings.supplierTypes,{selected:true}),
                    'code'
                );

                if($scope.feedSettings.pkgs.length) {
                    pkgNames = _.pluck($scope.feedSettings.pkgs,'name');
                }

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_MISCELLANEOUS,
                    'generateFeedUrl',
                    [{
                        naturalLanguageCode : userState.naturalLanguageCode(),
                        pkgNames : pkgNames,
                        limit : $scope.feedSettings.limit,
                        supplierTypes : supplierTypeCodes
                    }]
                ).then(
                    function(result) {
                        $scope.feedUrl = result.url;
                        amBuilding = false;
                    },
                    function(err) {
                        $log.error('unable to generate feed url');
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            /**
             * <p>This function will add a package to the list of specific packages using the
             * supplied name to identify the package.</p>
             */

            function initialAddPkgs() {

                var initialPkgNamesString = $location.search()['pkgNames'];

                if(initialPkgNamesString && initialPkgNamesString.length) {

                    // split on hyphen because it is not allowed in a package name.

                    _.each(initialPkgNamesString.split('-'),function(initialPkgNames) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_PKG,
                            'getPkg',
                            [
                                {
                                    naturalLanguageCode: userState.naturalLanguageCode(),
                                    name: initialPkgNames,
                                    versionType: 'NONE'
                                }
                            ]
                        ).then(
                            function (pkg) {
                                $scope.feedSettings.pkgs.push(pkg);
                            },
                            function () {
                                $log.warn('unable to initially populate the packages');
                            }
                        );
                    });
                }
            }

            initialAddPkgs();

            // ------------------
            // ACTIONS

            $scope.goBuild = function() {
                build();
            }

            $scope.goEdit = function() {
                $scope.feedUrl = undefined;
            }

            $scope.goRemovePkg = function(pkg) {
                $scope.feedSettings.pkgs = _.without($scope.feedSettings.pkgs,pkg);
            }

            /**
             * <P>This is made invalid as part of searching for the package.  If the package name is
             * changed then the invalidity no longer holds.</P>
             */

            $scope.$watch('pkgChooserName', function() {
                $scope.feedForm.pkgChooserName.$setValidity('notfound',true);
                $scope.feedForm.pkgChooserName.$setValidity('included',true);
            })

            $scope.goAddPkg = function() {

                // if the package is already in the list then the situation should be
                // avoided where the same package is added twice.

                if(_.findWhere(
                    $scope.feedSettings.pkgs,
                    { name : $scope.pkgChooserName })) {
                    $scope.feedForm.pkgChooserName.$setValidity('included',false);
                }
                else {

                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        'getPkg',
                        [
                            {
                                naturalLanguageCode: userState.naturalLanguageCode(),
                                name: $scope.pkgChooserName,
                                versionType: 'NONE'
                            }
                        ]
                    ).then(
                        function (pkg) {
                            $scope.pkgChooserName = '';
                            $scope.feedSettings.pkgs.push(pkg);
                        },
                        function (err) {

                            switch (err.code) {

                                case jsonRpc.errorCodes.OBJECTNOTFOUND:
                                    $scope.feedForm.pkgChooserName.$setValidity('notfound', false);
                                    break;

                                default:
                                    $log.error('unable to get the pkg for name; ' + $scope.pkgChooserName);
                                    errorHandling.handleJsonRpcError(err);
                                    break;

                            }

                        }
                    );
                }

            }

        }
    ]
);