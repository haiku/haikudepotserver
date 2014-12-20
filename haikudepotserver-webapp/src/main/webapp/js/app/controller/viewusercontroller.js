/*
 * Copyright 2013-2014, Andrew Lindesay
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

            $scope.shouldSpin = function() {
                return undefined == $scope.user;
            };

            refreshUser();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewUser($scope.user))
                ]);
            }

            function refreshUser() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_USER,
                        "getUser",
                        [{
                            nickname : $routeParams.nickname
                        }]
                    ).then(
                    function(result) {
                        $scope.user = result;
                        refreshBreadcrumbItems();
                        $log.info('fetched user; '+result.nickname);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.canLogout = function() {
                return userState.user() &&
                    $scope.user &&
                    userState.user().nickname == $scope.user.nickname;
            };

            $scope.goChangePassword = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createChangePassword($scope.user));
            };

            $scope.goEdit = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createEditUser($scope.user));
            };

            /**
             * <p>This method will logout the user; it will take them to the entry point for the application
             * and in doing so the page will be re-loaded and so their state will be removed.</p>
             */

            $scope.goLogout = function() {
                userState.user(null);
                breadcrumbs.resetAndNavigate([breadcrumbFactory.createHome()]);
            };

            $scope.canDeactivate = function() {
                return userState.user() &&
                    $scope.user &&
                    $scope.user.active &&
                    $scope.user.nickname != userState.user().nickname;
            };

            $scope.canReactivate = function() {
                return userState.user() &&
                    $scope.user &&
                    !$scope.user.active &&
                    $scope.user.nickname != userState.user().nickname;
            };

            $scope.goListJobs = function() {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createListJobs($scope.user));
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
                    function(result) {
                        $scope.user.active = flag;
                        $log.info('did update user active flag; '+flag);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            $scope.goDeactivate = function() {
                setActive(false);
            };

            $scope.goReactivate = function() {
                setActive(true);
            };

            /**
             * <p>This function will produce a spreadsheet of the user ratings for this
             * package.</p>
             */

            $scope.goDownloadUserRatings = function() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING,
                    'queueUserRatingSpreadsheetJob',
                    [{ userNickname: $routeParams.nickname }]
                ).then(
                    function(data) {
                        if(data.guid && data.guid.length) {
                            breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:data.guid }));
                        }
                        else {
                            $log.warn('attempt to run the user rating spreadsheet job failed');
                            // TODO; some sort of user-facing indication of this?
                        }
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            };

        }
    ]
);