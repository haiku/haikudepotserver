/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render the bar at the top of the screen that displays what this is and other details about
 * your usage of the application; maybe language, who is logged in and so on.</p>
 */

angular.module('haikudepotserver').directive('banner',function() {
    return {
        restrict: 'E',
        templateUrl:'/js/app/directive/banner.html',
        replace: true,
        controller:
            [
                '$rootScope','$scope','$log','$location','$route',
                'userState',
                function(
                    $rootScope,$scope,$log,$location,$route,
                    userState) {

                    $scope.goMore = function() {
                        $location.path('/more').search({});
                        return false;
                    }

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        $location.path('/').search({});
                        return false;
                    }

                    // --------------------------
                    // USER / AUTHENTICATION

                    function isLocationPathDisablingUserState() {
                        var p = $location.path();
                        return '/error' == p || '/authenticateuser' == p || '/createuser' == p;
                    }

                    $scope.canGoMore = function() {
                        var p = $location.path();
                        return '/error' != p && '/more' != p;
                    }

                    $scope.canAuthenticateOrCreate = function() {
                        return !isLocationPathDisablingUserState() && !userState.user();
                    }

                    $scope.canShowAuthenticated = function() {
                        return !isLocationPathDisablingUserState() && userState.user()
                    }

                    $scope.goViewUser = function() {
                        $location.path('/viewuser/'+userState.user().nickname).search({});
                        return false;
                    }

                    $scope.goCreateUser = function() {
                        $location.path('/createuser').search({});
                        return false;
                    }

                    $scope.goAuthenticateUser = function() {
                        var p = $location.path();
                        $location.path('/authenticateuser').search(
                            _.extend($location.search(), { destination: p }));
                        return false;
                    }

                    $scope.goLogoutUser = function() {
                        userState.user(null);
                        $location.path('/').search({});
                        return false;
                    }

                    $scope.userDisplayTitle = function() {
                        var data = userState.user();
                        return data ? data.nickname : undefined;
                    }

                }
            ]
    };
});