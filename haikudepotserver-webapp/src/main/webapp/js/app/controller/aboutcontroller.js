/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AboutController',
    [
        '$scope', '$log', '$location',
        'jsonRpc', 'constants', 'userState', 'runtimeInformation',
        'breadcrumbs', 'breadcrumbFactory',
        function(
            $scope, $log, $location,
            jsonRpc, constants, userState, runtimeInformation,
            breadcrumbs, breadcrumbFactory) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAbout())
            ]);

            $scope.serverProjectVersion = '...';

            function refreshRuntimeInformation() {
                runtimeInformation.getRuntimeInformation().then(
                    function(result) {
                        $scope.serverProjectVersion = result.projectVersion;
                    }
                );
            }

            refreshRuntimeInformation();

        }
    ]
);