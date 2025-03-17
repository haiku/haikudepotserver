/*
 * Copyright 2015-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgChangelogController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','errorHandling',
        'breadcrumbs','referenceData',
        'pkg','breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,errorHandling,
            breadcrumbs,referenceData,
            pkg,breadcrumbFactory) {

            var amSaving = false;

            $scope.pkg = undefined;
            $scope.changelog = {
                content: undefined
            };

            $scope.shouldSpin = function () {
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

                function (chain) {
                    pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                        function (result) {
                            $scope.pkg = result;
                            $log.info('found '+result.name+' pkg');
                            fnChain(chain);
                        },
                        errorHandling.handleRemoteProcedureCallError
                    );
                },

                function(chain) {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_PKG,
                        "get-pkg-changelog",
                        { pkgName : $scope.pkg.name }
                    ).then(
                        function (data) {
                            $scope.changelog.content = data.content;
                            fnChain(chain);
                        },
                        errorHandling.handleRemoteProcedureCallError
                    );
                },

                function(chain) {
                    refreshBreadcrumbItems();
                    fnChain(chain);
                }

            ]);

            $scope.goSave = function () {

                amSaving = true;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    "update-pkg-changelog",
                    {
                        pkgName : $scope.pkg.name,
                        content : $scope.changelog.content
                    }
                ).then(
                    function () {
                        $log.info('did save changelog content for; ' + $scope.pkg.name);
                        breadcrumbs.popAndNavigate();
                    },
                    function (err) {

                        switch (err.code) {
                            case remoteProcedureCall.errorCodes.VALIDATION:
                                errorHandling.relayValidationFailuresIntoForm(
                                    err.data, $scope.editPkgChangelogForm);
                                break;

                            default:
                                errorHandling.handleRemoteProcedureCallError(err);
                                break;
                        }

                    }
                ).finally(
                    function () {
                        amSaving = false;
                    }
                );
            }

        }
    ]
);
