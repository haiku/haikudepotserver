/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ReportsController',
    [
        '$scope','$log','$timeout',
        'jsonRpc','breadcrumbs','breadcrumbFactory','userState','constants',
        'errorHandling',
        function(
            $scope,$log,$timeout,
            jsonRpc,breadcrumbs,breadcrumbFactory,userState,constants,
            errorHandling) {

            $scope.didReject = false;
            $scope.didRejectTimeout = undefined;

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.createReports(),
            ]);

            /**
             * <p>A report may be enqueued (accepted), in which case a GUID will be supplied for the report
             * and the user should then be able to see the report in the "view job" page.  If the report is
             * rejected then a null / empty GUID will be supplied -- maybe because they were already running
             * the report?</p>
             */

            function navigateToViewJobOrNotifyRejection(guid) {

                function notifyRejection() {
                    $scope.didReject = true;

                    if($scope.didRejectTimeout) {
                        $timeout.cancel($scope.didRejectTimeout);
                    }

                    $scope.didRejectTimeout = $timeout(function() {
                        $scope.didReject = false;
                        $scope.didRejectTimeout = undefined;
                    }, 3000);
                }

                if(!guid || !guid.length) {
                    notifyRejection();
                }
                else {
                    breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:guid }));
                }
            }

            /**
             * <p>A number of reports are 'basic' in that there are no parameters and the only differentiating
             * feature is the method that is invoked to start it; this function unifies the code to start one
             * of these ones.</p>
             */

            function goBasicPkgReport(methodName) {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG_JOB,
                    methodName,
                    [{}]
                ).then(
                    function(data) {
                        navigateToViewJobOrNotifyRejection(data.guid);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.goPkgVersionLocalizationCoverageExportSpreadsheet = function() {
                goBasicPkgReport('queuePkgVersionLocalizationCoverageExportSpreadsheetJob');
            };

            $scope.goPkgLocalizationCoverageExportSpreadsheet = function() {
                goBasicPkgReport('queuePkgLocalizationCoverageExportSpreadsheetJob');
            };

            $scope.goPkgCategoryCoverageExportSpreadsheet = function() {
                goBasicPkgReport('queuePkgCategoryCoverageExportSpreadsheetJob');
            };

            $scope.goPkgProminenceAndUserRatingSpreadsheetReport = function() {
                goBasicPkgReport('queuePkgProminenceAndUserRatingSpreadsheetJob');
            };

            $scope.goPkgIconSpreadsheetReport = function() {
                goBasicPkgReport('queuePkgIconSpreadsheetJob');
            };

            $scope.goPkgScreenshotSpreadsheetReport = function() {
                goBasicPkgReport('queuePkgScreenshotSpreadsheetJob');
            };

            $scope.goPkgIconExportArchive = function() {
                goBasicPkgReport('queuePkgIconExportArchiveJob');
            };

            $scope.goPkgScreenshotExportArchive = function() {
                goBasicPkgReport('queuePkgScreenshotExportArchiveJob');
            };

            $scope.goAuthorizationRulesSpreadsheet= function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_AUTHORIZATION_JOB,
                    'queueAuthorizationRulesSpreadsheet',
                    [{}]
                ).then(
                    function(data) {
                        navigateToViewJobOrNotifyRejection(data.guid);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            $scope.goUserRatingSpreadsheetReportAll = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING_JOB,
                    'queueUserRatingSpreadsheetJob',
                    [{}] // no spec; get everything!
                ).then(
                    function(data) {
                        navigateToViewJobOrNotifyRejection(data.guid);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

        }
    ]
);