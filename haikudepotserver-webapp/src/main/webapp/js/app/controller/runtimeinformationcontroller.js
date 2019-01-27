/*
 * Copyright 2014-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'RuntimeInformationController',
    [
        '$scope','$log','$location',
        'jsonRpc','constants','userState',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        'runtimeInformation',
        function(
            $scope,$log,$location,
            jsonRpc,constants,userState,
            breadcrumbs,breadcrumbFactory,errorHandling,
            runtimeInformation) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.createAbout(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createRuntimeInformation())
            ]);

            $scope.serverStartTimestamp = undefined;
            $scope.versions = {
                angularFull : angular.version.full,
                serverProject : '...',
                serverJava : '...'
            };

            function refreshRuntimeInformation() {
                runtimeInformation.getRuntimeInformation().then(
                    function (result) {
                        $scope.versions.serverProject = result.projectVersion;
                        $scope.versions.serverJava = result.javaVersion;
                        $scope.serverStartTimestamp = result.startTimestamp;
                        $log.info('have fetched the runtime information');
                    }
                );
            }

            refreshRuntimeInformation();
        }
    ]
);