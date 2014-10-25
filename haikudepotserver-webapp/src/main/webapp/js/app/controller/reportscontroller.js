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

            $scope.goPkgCategoryCoverageSpreadsheetReport = function() {
                var iframeEl = document.getElementById("download-iframe");
                iframeEl.src = '/secured/pkg/pkgcategorycoveragespreadsheetreport/download.csv?hdsbtok=' +
                    userState.user().token +
                    '&rnd=' +
                    _.random(0,1000);
            }

        }
    ]
);