/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller displays the changelog for the package.</p>
 */

angular.module('haikudepotserver').controller(
    'ViewPkgChangelogController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','referenceData','pkg',
        function(
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,referenceData,pkg) {

            $scope.pkg = undefined;

            $scope.shouldSpin = function() {
                return undefined === $scope.pkg || undefined === $scope.pkg.changelog;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewPkgChangelog($scope.pkg))
                ]);
            }

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            fnChain([

                function (chain) {
                    pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                        function (result) {
                            $scope.pkg = result;
                            $log.info('found '+result.name+' pkg');
                            fnChain(chain);
                        },
                        function (err) {
                            errorHandling.handleRemoteProcedureCallError(err);
                        }
                    );
                },

                function (chain) {
                    remoteProcedureCall.call(
                      constants.ENDPOINT_API_V2_PKG,
                      "get-pkg-changelog",
                      { pkgName : $scope.pkg.name }
                    ).then(
                        function (data) {
                            $scope.pkg.changelog = {
                                content : data.content
                            };
                            fnChain(chain);
                        },
                        function (err) {
                            errorHandling.handleRemoteProcedureCallError(err);
                        }
                    );
                },

                function (chain) {
                    refreshBreadcrumbItems();
                    fnChain(chain);
                }

            ]);

        }
    ]
);
