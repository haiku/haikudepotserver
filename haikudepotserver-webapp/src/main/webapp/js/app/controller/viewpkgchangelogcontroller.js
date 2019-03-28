/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller displays the changelog for the package.</p>
 */

angular.module('haikudepotserver').controller(
    'ViewPkgChangelogController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','referenceData','pkg',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
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
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                },

                function (chain) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkgChangelog",
                        [{
                            pkgName : $scope.pkg.name
                        }]
                    ).then(
                        function (data) {
                            $scope.pkg.changelog = {
                                content : data.content
                            };
                            fnChain(chain);
                        },
                        function (err) {
                            errorHandling.handleJsonRpcError(err);
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