/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewRepositorySourceMirrorController',
    [
        '$scope', '$log', '$location', '$routeParams',
        'jsonRpc', 'constants', 'errorHandling', 'breadcrumbs',
        'breadcrumbFactory', 'referenceData',
        function(
            $scope, $log, $location, $routeParams,
            jsonRpc, constants, errorHandling, breadcrumbs,
            breadcrumbFactory, referenceData) {

            $scope.repositorySourceMirror = undefined;
            $scope.amDeleting = false;
            var amUpdatingActive = false;

            refetchRepositorySourceMirror();

            $scope.shouldSpin = function() {
                return !$scope.repositorySourceMirror;
            };

            function runUpdate(requestPayload) {
                amUpdatingActive = true;

                return jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "updateRepositorySourceMirror",
                    [_.extend({code : $scope.repositorySourceMirror.code}, requestPayload)]
                ).then(
                    function(data) {
                        amUpdatingActive = false;
                        return data;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            function updateActive(flag) {
                return runUpdate({
                    active : flag,
                    filter : [ 'ACTIVE' ]
                }).then(function() {
                    $scope.repositorySourceMirror.active = flag;
                    $log.info('did set the active flag on ' + $scope.repositorySourceMirror.code+' to ' + flag);
                });
            }

            $scope.canReactivate = function() {
                return $scope.repositorySourceMirror &&
                    !$scope.repositorySourceMirror.active &&
                    !amUpdatingActive;
            };

            $scope.canDeactivate = function() {
                return $scope.repositorySourceMirror &&
                    $scope.repositorySourceMirror.active &&
                    !$scope.repositorySourceMirror.isPrimary &&
                    !amUpdatingActive;
            };

            $scope.goReactivate = function() {
                updateActive(true);
            };

            $scope.goDeactivate = function() {
                updateActive(false);
            };

            $scope.canSetPrimary = function() {
                return $scope.repositorySourceMirror &&
                $scope.repositorySourceMirror.active &&
                !$scope.repositorySourceMirror.isPrimary &&
                !amUpdatingActive;
            };

            $scope.canRemove = function() {
                return $scope.repositorySourceMirror &&
                    !$scope.repositorySourceMirror.isPrimary &&
                    !amUpdatingActive;
            };

            $scope.goSetPrimary = function() {
                runUpdate({
                    isPrimary : true,
                    filter : ['IS_PRIMARY']
                }).then(
                    function() {
                        $scope.repositorySourceMirror.isPrimary = true;
                        $log.info('did configure [' + $scope.repositorySourceMirror.code + '] to primary');
                    }
                )
            };

            $scope.goEdit = function() {
                breadcrumbs.pushAndNavigate(
                    breadcrumbFactory.createEditRepositorySourceMirror($scope.repositorySourceMirror));
            };

            $scope.goRemove = function() {
                $scope.amDeleting = true;
            };

            $scope.goCancelRemove = function() {
                $scope.amDeleting = false;
            };

            $scope.goConfirmRemove = function() {
                if (!$scope.amDeleting) {
                    throw Error('cannot delete the mirror when not in delete mode');
                }

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_REPOSITORY,
                    "removeRepositorySourceMirror",
                    [{ code: $routeParams.repositorySourceMirrorCode }]
                ).then(
                    function() {
                        $scope.amDeleting = false;
                        $log.info('did remove the mirror [' +
                            $routeParams.repositorySourceMirrorCode + ']');
                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createListRepositories(),
                    breadcrumbFactory.createViewRepository($scope.repositorySourceMirror.repositorySource.repository),
                    breadcrumbFactory.createViewRepositorySource($scope.repositorySourceMirror.repositorySource),
                    breadcrumbFactory.applyCurrentLocation(
                        breadcrumbFactory.createViewRepositorySourceMirror($scope.repositorySourceMirror))
                ]);
            }

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            function refetchRepositorySourceMirror() {

                $scope.repositorySourceMirror = undefined;
                var repositorySourceMirror = undefined;

                fnChain([
                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepositorySourceMirror",
                            [{ code: $routeParams.repositorySourceMirrorCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepositorySource",
                            [{ code: repositorySourceMirror.repositorySourceCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror.repositorySource = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_REPOSITORY,
                            "getRepository",
                            [{ code: repositorySourceMirror.repositorySource.repositoryCode }]
                        ).then(
                            function(result) {
                                repositorySourceMirror.repositorySource.repository = result;
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );
                    },

                    function(chain) {
                        referenceData.countries().then(function(countries) {
                            var country = _.findWhere(
                                countries,
                                {code: repositorySourceMirror.countryCode});

                            if (!country) {
                                throw Error('unable to find the mirror country');
                            }

                            repositorySourceMirror.country = country;
                            fnChain(chain);
                        })
                    },

                    function(chain) {
                        $scope.repositorySourceMirror = repositorySourceMirror;
                        refreshBreadcrumbItems();
                        fnChain(chain);
                    }

                    ]);
            }

        }
    ]
);