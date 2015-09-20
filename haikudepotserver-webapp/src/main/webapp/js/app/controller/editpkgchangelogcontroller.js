/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgChangelogController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','errorHandling',
        'breadcrumbs','referenceData',
        'pkg','breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,errorHandling,
            breadcrumbs,referenceData,
            pkg,breadcrumbFactory) {

            var amSaving = false;

            $scope.pkg = undefined;
            $scope.changelog = {
                content: undefined
            };

            $scope.shouldSpin = function() {
                return undefined === $scope.pkg ||
                    undefined === $scope.changelog.content ||
                        amSaving;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.createViewPkgChangelog($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditPkgChangelog($scope.pkg))
                ]);
            }

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            fnChain([

                function(chain) {
                    pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                        function(result) {
                            $scope.pkg = result;
                            $log.info('found '+result.name+' pkg');
                            fnChain(chain);
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                },

                function(chain) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkgChangelog",
                        [{ pkgName : $scope.pkg.name }]
                    ).then(
                        function(data) {
                            $scope.changelog.content = data.content;
                            fnChain(chain);
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                },

                function(chain) {
                    refreshBreadcrumbItems();
                    fnChain(chain);
                }

            ]);

            $scope.goSave = function() {

                amSaving = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    "updatePkgChangelog",
                    [{
                        pkgName : $scope.pkg.name,
                        content : $scope.changelog.content
                    }]
                ).then(
                    function() {
                        $log.info('did save changelog content for; ' + $scope.pkg.name);
                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {

                        switch(err.code) {
                            case jsonRpc.errorCodes.VALIDATION:
                                errorHandling.handleValidationFailures(
                                    err.data.validationfailures,
                                    $scope.editPkgChangelogForm);
                                break;

                            default:
                                errorHandling.handleJsonRpcError(err);
                                break;
                        }

                    }
                ).finally(
                    function() {
                        amSaving = false;
                    }
                );
            }

        }
    ]
);
