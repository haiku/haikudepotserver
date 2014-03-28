/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewUserController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','errorHandling','messageSource','userState',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,errorHandling,messageSource,userState) {

            $scope.breadcrumbItems = undefined;
            $scope.user = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.user;
            };

            refreshUser();

            function refreshBreadcrumbItems() {
                $scope.breadcrumbItems = [{
                    title : $scope.user.nickname,
                    path : $location.path()
                }];
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
                $location.path('/user/' + $scope.user.nickname + '/changepassword').search({});
            }

            $scope.goEdit = function() {
                $location.path($location.path()+"/edit").search({});
            }

            $scope.goLogout = function() {
                userState.user(null);
                $location.path('/').search({});
            }

        }
    ]
);