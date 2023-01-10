/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AddEditRepositorySourceController',
    [
        '$scope','$log','$routeParams',
        'remoteProcedureCall','constants','breadcrumbs','breadcrumbFactory','userState','errorHandling',
        function(
            $scope,$log,$routeParams,
            remoteProcedureCall,constants,breadcrumbs,breadcrumbFactory,userState,errorHandling) {

            $scope.newExtraIdentifier = '';
            $scope.workingRepositorySource = undefined;
            $scope.amEditing = !!$routeParams.repositorySourceCode;
            var amSaving = false;

            $scope.shouldSpin = function() {
                return !$scope.workingRepositorySource || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addEditRepositorySourceForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // the validity of this field may have been set to false because of the
            // attempt to save it on the server-side.  This function will be hit when
            // the code changes so that validation does not apply.

            $scope.codeChanged = function() {
                $scope.addEditRepositorySourceForm.code.$setValidity('unique',true);
            };

            $scope.forcedInternalBaseUrlChanged = function() {
                $scope.addEditRepositorySourceForm.forcedInternalBaseUrl.$setValidity('malformed', true);
                $scope.addEditRepositorySourceForm.forcedInternalBaseUrl.$setValidity('trailingslash', true);
            };

            function refreshBreadcrumbItems() {

                var repository = { code : $routeParams.repositoryCode };

                var b = [
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.createViewRepository(repository)
                ];

                if ($scope.amEditing) {
                    b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditRepositorySource($scope.workingRepositorySource)));
                }
                else {
                    b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAddRepositorySource(repository)));
                }

                breadcrumbs.mergeCompleteStack(b);
            }

            function refreshRepositorySource() {
                if ($routeParams.repositorySourceCode) {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        "get-repository-source",
                        { code : $routeParams.repositorySourceCode }
                    ).then(
                        function(result) {
                            $scope.workingRepositorySource = _.clone(result);
                            refreshBreadcrumbItems();
                            $log.info('fetched repository source; '+result.code);
                        },
                        function(err) {
                            errorHandling.handleRemoteProcedureCallError(err);
                        }
                    );
                }
                else {
                    $scope.workingRepositorySource = {
                        repositoryCode : $routeParams.repositoryCode
                    };
                    refreshBreadcrumbItems();
                }
            }

            function refreshData() {
                refreshRepositorySource();
            }

            refreshData();

            $scope.goAddExtraIdentifier = function() {
                if ($scope.newExtraIdentifier &&
                    -1 === $scope.workingRepositorySource.extraIdentifiers.indexOf($scope.newExtraIdentifier)) {
                    $scope.workingRepositorySource.extraIdentifiers.push($scope.newExtraIdentifier);
                }
                $scope.newExtraIdentifier = '';
            };

            $scope.goDeleteExtraIdentifier = function(extraIdentifier) {
                if (extraIdentifier) {
                    $scope.workingRepositorySource.extraIdentifiers = _.without(
                        $scope.workingRepositorySource.extraIdentifiers,
                        extraIdentifier);
                }
            };

            $scope.goSave = function() {

                if ($scope.addEditRepositorySourceForm.$invalid) {
                    throw Error('expected the save of a repository source to only to be possible if the form is valid');
                }

                amSaving = true;

                if ($scope.amEditing) {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        "update-repository-source",
                        {
                            filter : [ 'FORCED_INTERNAL_BASE_URL', 'EXTRA_IDENTIFIERS' ],
                            forcedInternalBaseUrl : $scope.workingRepositorySource.forcedInternalBaseUrl,
                            extraIdentifiers: $scope.workingRepositorySource.extraIdentifiers,
                            code : $routeParams.repositorySourceCode
                        }
                    ).then(
                        function () {
                            $log.info('did update repository source; '+$routeParams.repositorySourceCode);
                            breadcrumbs.popAndNavigate();
                        },
                        function (err) {

                            switch (err.code) {
                                case remoteProcedureCall.errorCodes.VALIDATION:
                                    errorHandling.relayValidationFailuresIntoForm(
                                        err.data, $scope.addEditRepositorySourceForm);
                                    break;

                                default:
                                    errorHandling.handleRemoteProcedureCallError(err);
                                    break;
                            }

                            amSaving = false;
                        }
                    );
                }
                else {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        "create-repository-source",
                        {
                            repositoryCode : $scope.workingRepositorySource.repositoryCode,
                            code : $scope.workingRepositorySource.code
                        }
                    ).then(
                        function () {
                            $log.info('did create repository source; '+$scope.workingRepositorySource.code);
                            breadcrumbs.pop();
                            breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewRepositorySource($scope.workingRepositorySource));
                        },
                        function(err) {

                            switch (err.code) {
                                case remoteProcedureCall.errorCodes.VALIDATION:
                                    errorHandling.relayValidationFailuresIntoForm(
                                        err.data, $scope.addEditRepositorySourceForm);
                                    break;

                                default:
                                    errorHandling.handleRemoteProcedureCallError(err);
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
