/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewUserController',
    [
        '$scope','$log','$location','$routeParams','$window',
        'jsonRpc','constants','errorHandling','messageSource','userState','breadcrumbs',
        'breadcrumbFactory',
        function(
            $scope,$log,$location,$routeParams,$window,
            jsonRpc,constants,errorHandling,messageSource,userState,breadcrumbs,
            breadcrumbFactory) {

            $scope.breadcrumbItems = undefined;
            $scope.user = undefined;
            $scope.userUsageConditions = undefined;

            $scope.shouldSpin = function () {
                return undefined === $scope.user ||
                    ($scope.user.userUsageConditionsCode &&
                        undefined === $scope.userUsageConditions);
            };

            refreshUser();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewUser($scope.user))
                ]);
            }

            function fetchUserUsageConditions() {
                return jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "getUserUsageConditions",
                    [{code: $scope.user.userUsageConditionsAgreement.userUsageConditionsCode}])
                    .then(
                        function (userUsageConditionsData) {
                            $scope.userUsageConditions = userUsageConditionsData;
                        },
                        errorHandling.handleJsonRpcError
                    );
            }

            function refreshUser() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_USER,
                        "getUser",
                        [{
                            nickname : $routeParams.nickname
                        }]
                    ).then(
                    function (result) {
                        $scope.user = result;
                        refreshBreadcrumbItems();

                        if ($scope.user.userUsageConditionsAgreement &&
                            $scope.user.userUsageConditionsAgreement.userUsageConditionsCode) {
                            fetchUserUsageConditions();
                        }

                        $log.info('fetched user; '+result.nickname);
                    },
                    function (err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canLogout = function () {
                return userState.user() &&
                    $scope.user &&
                    userState.user().nickname === $scope.user.nickname;
            };

            /**
             * <p>This method will logout the user; it will take them to the entry point for the application
             * and in doing so the page will be re-loaded and so their state will be removed.</p>
             */

            $scope.goLogout = function () {
                userState.token(null);
                breadcrumbs.resetAndNavigate([breadcrumbFactory.createHome()]);
            };

            $scope.canDeactivate = function () {
                return userState.user() &&
                    $scope.user &&
                    $scope.user.active &&
                    $scope.user.nickname !== userState.user().nickname;
            };

            $scope.canReactivate = function () {
                return userState.user() &&
                    $scope.user &&
                    !$scope.user.active &&
                    $scope.user.nickname !== userState.user().nickname;
            };

            function setActive(flag) {
                $log.info('will update user active flag; '+flag);

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USER,
                    "updateUser",
                    [{
                        filter : [ 'ACTIVE' ],
                        nickname : $scope.user.nickname,
                        active : flag
                    }]
                ).then(
                    function () {
                        $scope.user.active = flag;
                        $log.info('did update user active flag; '+flag);
                    },
                    function (err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.goDeactivate = function () {
                setActive(false);
            };

            $scope.goReactivate = function () {
                setActive(true);
            };

            /**
             * <p>This function will produce a spreadsheet of the user ratings for this
             * package.</p>
             */

            $scope.goDownloadUserRatings = function () {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING_JOB,
                    'queueUserRatingSpreadsheetJob',
                    [{ userNickname: $routeParams.nickname }]
                ).then(
                    function (data) {
                        if (data.guid && data.guid.length) {
                            breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:data.guid }));
                        }
                        else {
                            $log.warn('attempt to run the user rating spreadsheet job failed');
                            // TODO; some sort of user-facing indication of this?
                        }
                    },
                    function (err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

        }
    ]
);