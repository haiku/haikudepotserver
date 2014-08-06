/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgProminenceController',
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

            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.selectedProminence = undefined;
            $scope.prominences = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || undefined == $scope.prominences || $scope.amSaving;
            };

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                    function(result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();

                        // now get the categories and from the data in the pkg it should be possible to
                        // pre-select those categories which are presently configured on the pkg.

                        referenceData.prominences().then(
                            function(data) {

                                $scope.prominences = _.clone(data);

                                // here we're not using the localization system because the names are so
                                // tightly bound to the ordering and nobody is going to see them anyway.

                                _.each(
                                    $scope.prominences,
                                    function(item) {
                                        item.title = item.name + ' (' + item.ordering + ')';
                                    }
                                );

                                $scope.selectedProminence = _.findWhere(
                                    $scope.prominences,
                                    { ordering : $scope.pkg.prominenceOrdering }
                                );

                            },
                            function() {
                                // logging happens inside
                                errorHandling.navigateToError();
                            }
                        )
                    },
                    function() {
                        errorHandling.navigateToError();
                    }
                );
            }

            refetchPkg();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromRouteParams($routeParams),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditPkgProminence($scope.pkg))
                ]);
            }

            // stores the categories back to the server for this package.  When it has done this, it will return to
            // view the pkg again.

            $scope.goStoreProminence = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    'updatePkgProminence',
                    [{
                        pkgName : $scope.pkg.name,
                        prominenceOrdering : $scope.selectedProminence.ordering
                    }]
                ).then(
                    function() {
                        $log.info('have updated the prominence for pkg '+$scope.pkg.name);
                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {
                        $log.error('unable to update pkg prominence');
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

        }
    ]
);
