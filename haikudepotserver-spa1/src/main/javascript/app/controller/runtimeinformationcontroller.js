/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'RuntimeInformationController',
    [
        '$scope','$log','$location',
        'constants','userState',
        'breadcrumbs','breadcrumbFactory','errorHandling',
        'runtimeInformation',"remoteProcedureCall",
        function(
            $scope,$log,$location,
            constants,userState,
            breadcrumbs,breadcrumbFactory,errorHandling,
            runtimeInformation, remoteProcedureCall) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.createAbout(),
                breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createRuntimeInformation())
            ]);

            $scope.jobStatusCounts = undefined;
            $scope.storage = undefined;
            $scope.serverStartTimestamp = undefined;
            $scope.versions = {
                angularFull : angular.version.full,
                serverProject : "...",
                serverJava : "...",
                serverOperatingSystem : "..."
            };

            function refreshRuntimeInformation() {
                runtimeInformation.getRuntimeInformation().then(
                    function (result) {
                        $scope.versions.serverProject = result.projectVersion;
                        $scope.versions.serverJava = result.javaVersion;
                        $scope.versions.serverOperatingSystem = result.operatingSystemVersion;
                        $scope.serverStartTimestamp = result.startTimestamp;
                        $scope.storage = result.storage;
                        $log.info('have fetched the runtime information');
                    }
                );

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_JOB, "job-status-counts", {}
                ).then(
                    function (result) {
                        $scope.jobStatusCounts = result.jobStatusCounts
                    },
                    errorHandling.handleRemoteProcedureCallError
                );
            }

            refreshRuntimeInformation();
        }
    ]
);
