/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ListPkgVersionsForPkgController',
    [
        '$scope','$log','$routeParams',
        'jsonRpc','constants','errorHandling',
        'breadcrumbs','breadcrumbFactory','pkg','userState',
        function(
            $scope,$log,$routeParams,
            jsonRpc,constants,errorHandling,
            breadcrumbs,breadcrumbFactory,pkg,userState) {

            $scope.pkg = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg;
            };

            // When the breadcrumbs are re-created, assume that we must have come through the main version first.

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.createListPkgVersionsForPkg($scope.pkg)
                ]);
            }

            function refetchPkg() {

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    'getPkg',
                    [{
                        name : $routeParams.name,
                        versionType : 'ALL',
                        incrementViewCounter : false,
                        naturalLanguageCode: userState.naturalLanguageCode(),
                    }]
                ).then(
                    function(result) {
                        $log.info('fetched '+result.name+' pkg with ' + result.versions.length + ' versions');
                        $scope.pkg = result;

                        // add a pkg.name onto each version so it is in the right structure for the pkg-version-label
                        // directive.

                        $scope.pkg.versions = _.map($scope.pkg.versions,function(v) {
                            return _.extend(v,{ pkg : { name : $scope.pkg.name }});
                        });

                        refreshBreadcrumbItems();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError();
                    }
                );
            }

            refetchPkg();

        }
    ]
);
