/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'PkgCategoryCoverageImportSpreadsheetController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','pkg','jobs',
        function(
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,pkg,jobs) {

            var IMPORT_SIZE_LIMIT = 256 * 1024; // 100k

            $scope.specification = {
                importDataFile : undefined
            };

            $scope.amQueueing = false;

            $scope.shouldSpin = function () {
                return $scope.amQueueing;
            };

            $scope.deriveFormControlsContainerClasses = function (name) {
                return $scope.specificationForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createRootOperations(),
                    breadcrumbFactory.createPkgCategoryCoverageImportSpreadsheet()
                ]);
            }

            refreshBreadcrumbItems();

            // This function will check to make sure that the file is not too large or too small to be a valid
            // input for this importation process.

            function validateImportDataFile(file, model) {
                model.$setValidity('badsize',undefined === file || !(file.size > 24 && file.size < IMPORT_SIZE_LIMIT));
            }

            function importDataFileDidChange() {
                validateImportDataFile($scope.specification.importDataFile, $scope.specificationForm['importDataFile']);
            }

            $scope.$watch('specification.importDataFile', function() {
                importDataFileDidChange();
            });


            // This function will take the data from the form and load in the new pkg icons

            $scope.goQueue = function() {

                if($scope.specificationForm.$invalid) {
                    throw Error('expected the import of package categories only to be possible if the form is valid');
                }

                $scope.amQueueing = true;

                // uploads the import data to the server so it can be used later.

                jobs.supplyData($scope.specification.importDataFile).then(
                    function (guid) {
                      $log.info('did upload import data to the server; ' + guid);

                      remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_PKG_JOB,
                            "queue-pkg-category-coverage-import-spreadsheet-job",
                            { inputDataGuid: guid }
                        ).then(
                            function (result) {
                                $log.info('did queue pkg category coverage import spreadsheet job; ' + result.guid);
                                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:result.guid }));
                                $scope.amQueueing = false;
                            },
                            function (err) {
                                $scope.amQueueing = false;
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );

                    },
                    function () {
                        $log.error('failed to upload import data to the server');
                        errorHandling.navigateToError();
                        $scope.amQueueing = false;
                    }
                );

            }; // goQueue

        }
    ]
);
