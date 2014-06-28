/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewUserController',
    [
        '$scope','$log','$location','$routeParams','$window',
        'jsonRpc','constants','errorHandling','messageSource','userState','breadcrumbs',
        function(
            $scope,$log,$location,$routeParams,$window,
            jsonRpc,constants,errorHandling,messageSource,userState,breadcrumbs) {

            $scope.breadcrumbItems = undefined;
            $scope.user = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.user;
            };

            refreshUser();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.applyCurrentLocation(breadcrumbs.createViewUser($scope.user))
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
            };

            $scope.canLogout = function() {
                return userState.user() &&
                    $scope.user &&
                    userState.user().nickname == $scope.user.nickname;
            }

            $scope.goChangePassword = function() {
                breadcrumbs.pushAndNavigate(breadcrumbs.createChangePassword($scope.user));
            }

            $scope.goEdit = function() {
                breadcrumbs.pushAndNavigate(breadcrumbs.createEditUser($scope.user));
            }

            /**
             * <p>This method will logout the user; it will take them to the entry point for the application
             * and in doing so the page will be re-loaded and so their state will be removed.</p>
             */

            $scope.goLogout = function() {
                $window.location.href = '/';
            }

            $scope.canDeactivate = function() {
                return $scope.user &&
                    $scope.user.active &&
                    $scope.user.nickname != userState.user().nickname;
            }

            $scope.canReactivate = function() {
                return $scope.user &&
                    !$scope.user.active &&
                    $scope.user.nickname != userState.user().nickname;
            }

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
            }

            $scope.goReactivate = function() {
                setActive(true);
            }

        }
    ]
);