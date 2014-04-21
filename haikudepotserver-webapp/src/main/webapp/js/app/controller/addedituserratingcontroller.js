/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

// WORK IN PROGRESS!

angular.module('haikudepotserver').controller(
    'AddEditUserRatingController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','userState','errorHandling','referenceData',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,userState,errorHandling,referenceData) {

            $scope.workingUserRating = undefined;
            $scope.userRatingStabilities = undefined;
            $scope.amEditing = !!$routeParams.code;
            $scope.pkg = undefined;
            var amSaving = false;

            $scope.shouldSpin = function() {
                return undefined == $scope.workingUserRating || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addEditUserRatingForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // this function will execute the last item in the chain and pass the remainder of
            // the chain into the function to be latterly executed.  This allows a series of
            // discrete functions of work to proceed serially.

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            fnChain([

                // user rating stabilities reference data.

                function(chain) {
                    referenceData.userRatingStabilities().then(
                        function(data) {
                            $scope.userRatingStabilities = data;
                            fnChain(chain);
                        },
                        function() { // logging done already
                            errorHandling.navigateToError();
                        }

                    )
                },

                // get the package for which the rating is for.

                function(chain) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        'getPkg',
                        [{
                            name: $routeParams.name,
                            versionType : 'LATEST',
                            incrementViewCounter : false,
                            architectureCode : $routeParams.architectureCode,
                            naturalLanguageCode: userState.naturalLanguageCode()
                        }]
                    ).then(
                        function(result) {
                            $scope.pkg = result;
                            fnChain(chain);
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                        }
                    );
                },

                // working rating data; either this is an add rating or it is an edit rating and in the latter
                // case we actually have to get the data for the rating downloaded.

                function(chain) {
                  TODO
                },

                // breadcrumbs

                function(chain) {
                    var b = [
                        breadcrumbs.createHome(),
                        breadcrumbs.createViewPkg(
                            $scope.pkg,
                            $routeParams.version,
                            $routeParams.architectureCode)
                    ];

                    if($scope.amEditing) {
                        b.push({
                            titleKey : 'breadcrumb.editUserRating.title',
                            path : $location.path()
                        });
                    }
                    else {
                        b.push({
                            titleKey : 'breadcrumb.addUserRating.title',
                            path : $location.path()
                        })
                    }

                    breadcrumbs.mergeCompleteStack(b);

                    fnChain(chain);
                }

            ]);











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
                        $location.path("/error").search({});
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
                            $location.path('/repository/'+$scope.workingRepository.code).search({});
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