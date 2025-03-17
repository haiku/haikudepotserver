/*
 * Copyright 2013-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ListPkgVersionsForPkgController',
    [
        '$scope','$log','$routeParams',
        'remoteProcedureCall','constants','errorHandling',
        'breadcrumbs','breadcrumbFactory','pkg','userState',
        'repositoryService',
        function(
            $scope,$log,$routeParams,
            remoteProcedureCall,constants,errorHandling,
            breadcrumbs,breadcrumbFactory,pkg,userState,
            repositoryService) {

            $scope.pkg = undefined;

            $scope.shouldSpin = function() {
                return undefined === $scope.pkg;
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

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    'get-pkg',
                    {
                        name : $routeParams.name,
                        versionType : 'ALL',
                        incrementViewCounter : false,
                        naturalLanguageCode: userState.naturalLanguageCode()
                    }
                ).then(
                    function (result) {
                        $log.info('fetched '+result.name+' pkg with ' + result.versions.length + ' versions');
                        $scope.pkg = result;

                        // add a pkg.name onto each version so it is in the right structure for the pkg-version-label
                        // directive.

                        $scope.pkg.versions = _.map($scope.pkg.versions,function(v) {
                                return _.extend(v,{ pkg : { name : $scope.pkg.name }});
                            });

                        refreshBreadcrumbItems();

                        // add repositories in.

                        var repositories = repositoryService.getRepositories().then(
                            function(repositories) {
                                _.each($scope.pkg.versions, function(pv) {
                                    pv.repository = _.findWhere(repositories, {code : pv.repositoryCode });

                                    if (!pv.repository) {
                                        throw Error('the repository was not able to be found for the code "' + pv.repositoryCode + '"');
                                    }
                                });
                            },
                            function() {
                                $log.error('unable to get the repositories');
                                errorHandling.navigateToError();
                            }
                        );
                    },
                    errorHandling.handleRemoteProcedureCallError
                );
            }

            refetchPkg();

            // --------------------------
            // ACTIONS

            // This is a bit strange; we're cycling through a list of package versions, but
            // need to make the argument to the function look as if we are only looking at
            // one version.

            $scope.goViewLocalization = function(pkgVersion) {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewPkgVersionLocalization({
                    name : pkgVersion.pkg.name,
                    versions : [ pkgVersion ]
                }));
            };

        }
    ]
);
