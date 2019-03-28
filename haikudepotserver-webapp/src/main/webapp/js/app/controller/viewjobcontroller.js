/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewJobController',
    [
        '$scope','$log','$location','$routeParams','$window',
        'jsonRpc','constants','errorHandling','messageSource','userState','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$window,
            jsonRpc,constants,errorHandling,messageSource,userState,breadcrumbs,
            breadcrumbFactory) {

            $scope.breadcrumbItems = undefined;
            $scope.job = undefined;

            $scope.shouldSpin = function () {
                return undefined === $scope.job;
            };

            function refreshBreadcrumbItems() {

                if ($scope.job.ownerUserNickname) {
                    breadcrumbs.mergeCompleteStack([
                        breadcrumbFactory.createHome(),
                        breadcrumbFactory.createListUsers(),
                        breadcrumbFactory.createViewUser({ nickname: $scope.job.ownerUserNickname }),
                        breadcrumbFactory.createListJobs({ nickname: $scope.job.ownerUserNickname }),
                        breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewJob($scope.job))
                    ]);
                }
                else {
                    breadcrumbs.mergeCompleteStack([
                        breadcrumbFactory.createHome(),
                        breadcrumbFactory.createRootOperations(),
                        breadcrumbFactory.createListJobs(),
                        breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewJob($scope.job))
                    ]);
                }

            }

            function refreshJob() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_JOB,
                    "getJob",
                    [{ guid : $routeParams.guid }]
                ).then(
                    function (result) {
                        $scope.job = result;
                        $scope.job.ownerUser = {
                            nickname : $scope.job.ownerUserNickname
                        };
                        refreshBreadcrumbItems();
                        $log.info('fetched job; '+result.guid);
                    },
                    function (err) {
                        switch (err.code) {

                            case jsonRpc.errorCodes.OBJECTNOTFOUND:
                                if (err.data.entityName === 'Job') {
                                    $scope.job = {};
                                }
                                else {
                                    errorHandling.handleJsonRpcError(err);
                                }
                                break;

                            default:
                                errorHandling.handleJsonRpcError(err);
                                break;
                        }


                    }
                );
            }

            refreshJob();

            $scope.goRefresh = function () {
                refreshJob();
            };

            /**
             * <p>This will trigger when the user clicks on a link in the
             * @param jobData
             */

            $scope.goDownloadData = function(jobData) {

                if(!jobData || !jobData.guid) {
                    throw Error('bad job data');
                }

                var iframeEl = document.getElementById("download-iframe");

                if (!iframeEl) {
                    throw Error('am not able to find the \'download-iframe\'');
                }

                iframeEl.src = '/__secured/jobdata/'+jobData.guid+'/download?hdsbtok=' +
                userState.token() +
                '&rnd=' +
                _.random(0,1000);

            };

        }
    ]
);