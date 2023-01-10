/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AddEditRepositorySourceMirrorController',
    [
        '$scope', '$log', '$routeParams',
        'remoteProcedureCall', 'constants', 'breadcrumbs', 'breadcrumbFactory', 'userState', 'errorHandling',
        'referenceData',
        function (
            $scope, $log, $routeParams,
            remoteProcedureCall, constants, breadcrumbs, breadcrumbFactory, userState, errorHandling,
            referenceData) {

            $scope.workingRepositorySourceMirror = undefined;
            $scope.amEditing = !!$routeParams.repositorySourceMirrorCode;
            $scope.allCountries = undefined;
            var amSaving = false;

            $scope.shouldSpin = function () {
                return !$scope.workingRepositorySourceMirror || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function (name) {
                return $scope.addEditRepositorySourceMirrorForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            $scope.forcedBaseUrlChanged = function () {
                $scope.addEditRepositorySourceMirrorForm.baseUrl.$setValidity('malformed', true);
                $scope.addEditRepositorySourceMirrorForm.baseUrl.$setValidity('unique', true);
            };

            function refreshBreadcrumbItems() {

                var repository = {code: $routeParams.repositoryCode};

                var b = [
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.createViewRepository(repository)
                ];

                if ($scope.amEditing) {
                    b.push(breadcrumbFactory.applyCurrentLocation(
                        breadcrumbFactory.createEditRepositorySourceMirror(
                            $scope.workingRepositorySourceMirror)));
                }
                else {
                    b.push(breadcrumbFactory.applyCurrentLocation(
                        breadcrumbFactory.createAddRepositorySourceMirror(
                            $scope.workingRepositorySourceMirror.repositorySource)));
                }

                breadcrumbs.mergeCompleteStack(b);
            }

            function refreshData() {

                function fnChain(chain) {
                    if (chain && chain.length) {
                        chain.shift()(chain);
                    }
                }

                $scope.workingRepositorySourceMirror = undefined;
                var workingRepositorySourceMirror = undefined;
                var refreshDataChain = [];

                if ($routeParams.repositorySourceMirrorCode) {
                    refreshDataChain.push(function (chain) {
                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_REPOSITORY,
                            "get-repository-source-mirror",
                            { code: $routeParams.repositorySourceMirrorCode }
                        ).then(
                            function (result) {
                                workingRepositorySourceMirror = _.clone(result);
                                $log.info('fetched repository source mirror [' + result.code + ']');
                                fnChain(chain);
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    });
                } else {
                    workingRepositorySourceMirror = {
                        repositorySourceCode: $routeParams.repositorySourceCode
                    };
                }

                refreshDataChain.push(
                    function (chain) {
                        var repositorySourceCode = workingRepositorySourceMirror.repositorySourceCode;
                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_REPOSITORY,
                            "get-repository-source",
                            { code: repositorySourceCode }
                        ).then(
                            function (result) {
                                $log.info('did fetch repository source [' + repositorySourceCode + ']');
                                workingRepositorySourceMirror.repositorySource = result;
                                fnChain(chain);
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    },

                    function (chain) {
                        var repositoryCode = workingRepositorySourceMirror.repositorySource.repositoryCode;
                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_REPOSITORY,
                            "get-repository",
                            { code: repositoryCode }
                        ).then(
                            function (result) {
                                $log.info('did fetch repository [' + repositoryCode + "]");
                                workingRepositorySourceMirror.repositorySource.repository = result;
                                fnChain(chain);
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    },

                    function (chain) {
                        referenceData.countries()
                            .then(function (countries) {
                                $log.info('did fetch countries');
                                $scope.allCountries = countries;
                                var countryCode = workingRepositorySourceMirror.countryCode;
                                var country = _.findWhere(countries, {code: !!countryCode ? countryCode : 'DE'});

                                if (!country) {
                                    throw Error('unable to establish the initial country');
                                }

                                workingRepositorySourceMirror.country = country;
                                fnChain(chain);
                            });
                    },

                    function (chain) {
                        $scope.workingRepositorySourceMirror = workingRepositorySourceMirror;
                        refreshBreadcrumbItems();
                        fnChain(chain);
                    }
                );

                fnChain(refreshDataChain);
            }

            refreshData();

            $scope.goSave = function () {

                function handleErrorResponse(err) {
                    switch (err.code) {
                        case remoteProcedureCall.errorCodes.VALIDATION:
                            errorHandling.relayValidationFailuresIntoForm(
                                err.data, $scope.addEditRepositorySourceMirrorForm);
                            break;

                        default:
                            errorHandling.handleRemoteProcedureCallError(err);
                            break;
                    }

                    amSaving = false;
                }

                if ($scope.addEditRepositorySourceMirrorForm.$invalid) {
                    throw Error('expected the save of a repository source mirror ' +
                        'to only to be possible if the form is valid');
                }

                amSaving = true;

                if ($scope.amEditing) {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        "update-repository-source-mirror",
                        {
                            filter: ['DESCRIPTION', 'COUNTRY', 'BASE_URL'],
                            code: $scope.workingRepositorySourceMirror.code,
                            baseUrl: $scope.workingRepositorySourceMirror.baseUrl,
                            countryCode: $scope.workingRepositorySourceMirror.country.code,
                            description: $scope.workingRepositorySourceMirror.description
                        }
                    ).then(
                        function () {
                            $log.info('did update repository source mirror; ' +
                                $scope.workingRepositorySourceMirror.code);
                            breadcrumbs.popAndNavigate();
                        },
                        handleErrorResponse
                    );
                }
                else {
                    remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_REPOSITORY,
                        "create-repository-source-mirror",
                        {
                            repositorySourceCode: $scope.workingRepositorySourceMirror.repositorySource.code,
                            baseUrl: $scope.workingRepositorySourceMirror.baseUrl,
                            countryCode: $scope.workingRepositorySourceMirror.country.code,
                            description: $scope.workingRepositorySourceMirror.description
                        }
                    ).then(
                        function (data) {
                            $log.info('did create repository source mirror [' + data.code + ']');
                            $scope.workingRepositorySourceMirror.code = data.code;
                            breadcrumbs.pop();
                            breadcrumbs.pushAndNavigate(breadcrumbFactory
                                .createViewRepositorySourceMirror($scope.workingRepositorySourceMirror));
                        },
                        handleErrorResponse
                    );
                }

            }

        }
    ]
);
