/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewPkgController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','userState',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,userState) {

            $scope.breadcrumbItems = undefined;
            $scope.pkg = undefined;

            refetchPkg();

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg;
            }

            $scope.homePageLink = function() {
                var u = undefined;

                if($scope.pkg) {
                    u = _.find(
                        $scope.pkg.versions[0].urls,
                        function(url) {
                            return url.urlTypeCode == 'homepage';
                        });
                }

                return u ? u.url : undefined;
            }

            function refreshBreadcrumbItems() {
                $scope.breadcrumbItems = [{
                    title : $scope.pkg.name,
                    path : $location.path()
                }];
            }

            function refetchPkg() {

                userState.architecture().then(
                    function(architecture) {
                        jsonRpc.call(
                                constants.ENDPOINT_API_V1_PKG,
                                "getPkg",
                                [{
                                    name: $routeParams.name,
                                    versionType: 'LATEST',
                                    architectureCode: architecture.code
                                }]
                            ).then(
                            function(result) {
                                $scope.pkg = result;
                                $log.info('found '+result.name+' pkg');
                                refreshBreadcrumbItems();
                            },
                            function(err) {
                                constants.ERRORHANDLING_JSONRPC(err,$location,$log);
                            }
                        );
                    },
                    function() {
                        $log.error('a problem has arisen getting the architecture');
                        $location.path("/error").search({});
                    }

                );
            }

        }
    ]
);