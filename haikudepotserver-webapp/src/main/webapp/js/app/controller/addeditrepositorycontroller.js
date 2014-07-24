/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AddEditRepositoryController',
    [
        '$scope','$log','$routeParams',
        'jsonRpc','constants','breadcrumbs','breadcrumbFactory','userState','errorHandling','referenceData',
        function(
            $scope,$log,$routeParams,
            jsonRpc,constants,breadcrumbs,breadcrumbFactory,userState,errorHandling,referenceData) {

            $scope.workingRepository = undefined;
            $scope.architectures = undefined;
            $scope.amEditing = !!$routeParams.code;
            var amSaving = false;

            $scope.shouldSpin = function() {
                return undefined == $scope.architectures || undefined == $scope.workingRepository || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addEditRepositoryForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // the validity of this field may have been set to false because of the
            // attempt to save it on the server-side.  This function will be hit when
            // the code changes so that validation does not apply.

            $scope.codeChanged = function() {
                $scope.addEditRepositoryForm.code.$setValidity('unique',true);
            };

            $scope.urlChanged = function() {
                $scope.addEditRepositoryForm.url.$setValidity('malformed',true);
            };

            function refreshBreadcrumbItems() {

                var b = [
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories()
                ];

                if($scope.amEditing) {
                    b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditRepository($scope.workingRepository)));
                }
                else {
                    b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAddRepository()));
                }

                breadcrumbs.mergeCompleteStack(b);
            }

            function refreshRepository() {
                if($routeParams.code) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_REPOSITORY,
                        "getRepository",
                        [{ code : $routeParams.code }]
                    ).then(
                        function(result) {
                            $scope.workingRepository = {
                                code : result.code,
                                url : result.url,
                                architecture : _.find(
                                    $scope.architectures, function(a) {
                                        return a.code == result.architectureCode;
                                    })
                            };
                            refreshBreadcrumbItems();
                            $log.info('fetched repository; '+result.code);
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                }
                else {
                    $scope.workingRepository = {
                        architecture : $scope.architectures[0]
                    };
                    refreshBreadcrumbItems();
                }
            }

            function refreshArchitectures() {
                referenceData.architectures().then(
                    function(data) {
                        $scope.architectures = data;
                        refreshRepository();
                    },
                    function() { // error logged already
                        errorHandling.navigateToError();
                    }
                );
            }

            function refreshData() {
                refreshArchitectures();
            }

            refreshData();

            $scope.goSave = function() {

                if($scope.addEditRepositoryForm.$invalid) {
                    throw 'expected the save of a repository to only to be possible if the form is valid';
                }

                amSaving = true;

                if($scope.amEditing) {
                    jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "updateRepository",
                            [{
                                filter : [ 'URL' ],
                                url : $scope.workingRepository.url,
                                code : $scope.workingRepository.code
                            }]
                        ).then(
                        function() {
                            $log.info('did update repository; '+$scope.workingRepository.code);
                            breadcrumbs.popAndNavigate();
                        },
                        function(err) {

                            switch(err.code) {
                                case jsonRpc.errorCodes.VALIDATION:
                                    errorHandling.handleValidationFailures(
                                        err.data.validationfailures,
                                        $scope.addEditRepositoryForm);
                                    break;

                                default:
                                    errorHandling.handleJsonRpcError(err);
                                    break;
                            }

                            amSaving = false;
                        }
                    );
                }
                else {
                    jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "createRepository",
                            [{
                                architectureCode : $scope.workingRepository.architecture.code,
                                url : $scope.workingRepository.url,
                                code : $scope.workingRepository.code
                            }]
                        ).then(
                        function() {
                            $log.info('did create repository; '+$scope.workingRepository.code);
                            breadcrumbs.pop();
                            breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewRepository($scope.workingRepository));
                        },
                        function(err) {

                            switch(err.code) {
                                case jsonRpc.errorCodes.VALIDATION:
                                    errorHandling.handleValidationFailures(
                                        err.data.validationfailures,
                                        $scope.addEditRepositoryForm);
                                    break;

                                default:
                                    errorHandling.handleJsonRpcError(err);
                                    break;
                            }

                            amSaving = false;
                        }
                    );
                }

            }

        }
    ]
);