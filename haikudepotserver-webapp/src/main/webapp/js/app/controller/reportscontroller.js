/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ReportsController',
    [
        '$scope','$log',
        'breadcrumbs','breadcrumbFactory','userState',
        function(
            $scope,$log,
            breadcrumbs,breadcrumbFactory,userState) {

            breadcrumbs.mergeCompleteStack([
                breadcrumbFactory.createHome(),
                breadcrumbFactory.createReports(),
            ]);

            // the random aspect here is so that it is unlikely to cache the result.

            function goSecuredCsvReport(pathPart) {
                var iframeEl = document.getElementById("download-iframe");
                iframeEl.src = '/secured/'+pathPart+'/download.csv?hdsbtok=' +
                userState.user().token +
                '&rnd=' +
                _.random(0,1000);
            }

            $scope.goPkgCategoryCoverageSpreadsheetReport = function() {
                goSecuredCsvReport('pkg/pkgcategorycoveragespreadsheetreport');
            };

            $scope.goPkgProminenceAndUserRatingSpreadsheetReport = function() {
                goSecuredCsvReport('pkg/pkgprominenceanduserratingspreadsheetreport');
            };

            $scope.goPkgIconSpreadsheetReport = function() {
                goSecuredCsvReport('pkg/pkgiconspreadsheetreport');
            };

            $scope.goUserRatingSpreadsheetReportAll = function() {
                goSecuredCsvReport('userrating/userratingspreadsheetreport');
            };

        }
    ]
);