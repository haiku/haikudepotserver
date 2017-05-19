/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'PkgScreenshotArchiveImportController',
    [
        '$scope', '$log', '$location', '$routeParams',
        'jsonRpc', 'constants', 'errorHandling', 'messageSource', 'userState',
        'breadcrumbs', 'breadcrumbFactory', 'jobs',
        function(
            $scope, $log, $location, $routeParams,
            jsonRpc, constants, errorHandling, messageSource, userState,
            breadcrumbs, breadcrumbFactory, jobs) {

            var IMPORT_SIZE_LIMIT = 20 * 1024 * 1024; // 20MB

            $scope.importStrategies = [
                {
                    code : 'AUGMENT',
                    title : undefined
                },
                {
                    code : 'REPLACE',
                    title : undefined
                }
            ];

            $scope.specification = {
                importDataFile : undefined,
                importStrategy : _.find($scope.importStrategies, function(is) { return is.code == 'AUGMENT'; })
            };

            $scope.showHelp = false;
            $scope.amQueueing = false;

            _.each($scope.importStrategies, function(is) {
                is.title = is.code;
                messageSource.get(
                    userState.naturalLanguageCode(),
                    'pkgScreenshotArchiveImport.importStrategy.' + is.code.toLowerCase() + '.title').then(
                    function (localizedString) {
                        is.title = localizedString;
                    },
                    function () {
                        is.title = localizedString = '???';
                    }
                );
            });

            $scope.shouldSpin = function() {
                return $scope.amQueueing;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.specificationForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createRootOperations(),
                    breadcrumbFactory.createPkgScreenshotArchiveImport()
                ]);
            }

            refreshBreadcrumbItems();

            // This function will check to make sure that the file is not too large or too small to be a valid
            // input for this importation process.

            function validateImportDataFile(file, model) {
                model.$setValidity('badsize',undefined==file || (file.size > 8 && file.size < IMPORT_SIZE_LIMIT));
            }

            function importDataFileDidChange() {
                validateImportDataFile($scope.specification.importDataFile, $scope.specificationForm['importDataFile']);
            }

            $scope.$watch('specification.importDataFile', function() {
                importDataFileDidChange();
            });

            $scope.goShowHelp = function() {
                $scope.showHelp = !$scope.showHelp;
            };

            // This function will take the data from the form and load in the new pkg icons

            $scope.goQueue = function() {

                if($scope.specificationForm.$invalid) {
                    throw Error('expected the import of pkg screenshot archive only to be possible if the form is valid');
                }

                $scope.amQueueing = true;

                // uploads the import data to the server so it can be used later.

                jobs.supplyData($scope.specification.importDataFile).then(
                    function(guid) {
                        $log.info('did upload import data to the server; ' + guid);

                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_PKG_JOB,
                            "queuePkgScreenshotArchiveImportJob",
                            [{ inputDataGuid: guid, importStrategy: $scope.specification.importStrategy.code }]
                        ).then(
                            function(result) {
                                $log.info('did queue pkg screenshot archive import job; ' + result.guid);
                                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:result.guid }));
                                $scope.amQueueing = false;
                            },
                            function(err) {
                                $scope.amQueueing = false;
                                errorHandling.handleJsonRpcError(err);
                            }
                        );

                    },
                    function() {
                        $log.error('failed to upload import data to the server');
                        errorHandling.navigateToError();
                        $scope.amQueueing = false;
                    }
                );

            }; // goQueue

        }
    ]
);
