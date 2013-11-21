/*
 * Copyright 2013, Andrew Lindesay
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
                'userState','referenceData',
                function(
                    $rootScope,$scope,$log,$location,$route,
                    userState,referenceData) {

                    // these are the architectures that the user can choose from.

                    $scope.architectures = [];

                    // this is the architecture that is presently chosen by the user.

                    $scope.architecture = undefined;

                    // this is a list of actions that are possible around the user menu.  This will change depending
                    // on the rute that is presently being shown.

                    $scope.userActions = [];

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        $location.path('/').search({});
                        return false;
                    }

                    // --------------------------
                    // ROUTE UPDATE HANDLING

                    // when the route is changed, we want to be able to modify the behavior of the banner a bit; hide
                    // some things and show others.  This hook will allow the controller to observe that the route
                    // has changed.

                    $rootScope.$on('$locationChangeSuccess', function() {
                        refreshArchitectures();
                        refreshUserActions();
                    });

                    // --------------------------
                    // ARCHITECTURES

                    // only show the architectures when the user is viewing something to do with packages.

                    function canShowArchitectures() {
                        var p = $location.path();
                        return '/' == p || 0 == p.indexOf('/viewpkg/');
                    }

                    function refreshArchitectures() {
                        if(canShowArchitectures()) {
                            referenceData.architectures().then(
                                function(architectures) {
                                    $scope.architectures = architectures;
                                },
                                function() {
                                    $log.error('a problem has arisen obtaining the architectures');
                                    $location.path("/error").search({});
                                }
                            );
                        }
                        else {
                            $scope.architectures = [];
                        }
                    }

                    function refreshArchitecture() {
                        userState.architecture().then(
                            function(data) {
                                $scope.architecture = data;
                            },
                            function() {
                                $log.error('a problem has arisen obtaining the architecture');
                                $location.path("/error").search({});
                            }
                        );
                    }

                    $scope.isArchitectureSelected = function(a) {
                        return $scope.architecture && $scope.architecture.code == a.code;
                    }

                    $scope.goChooseArchitecture = function(a) {

                        if(!$scope.isArchitectureSelected(a)) {

                            userState.architecture(a);
                            refreshArchitecture();

                            if($location.path() == '/') {
                                $route.reload();
                            }
                            else {
                                $location.path('/').search({});
                            }

                        }

                        return false;
                    }

                    refreshArchitectures();
                    refreshArchitecture();

                    // --------------------------
                    // USER / AUTHENTICATION

                    function refreshUserActions() {
                        var p = $location.path();

                        if('/error' == p || '/authenticateuser' == p || '/createuser' == p) {
                            $scope.userActions = [];
                        }
                        else {
                            var a = [];

                            if(!userState.user()) {

                                a.push({
                                    titleKey : 'banner.userActions.authenticateUser',
                                    action : $scope.goAuthenticateUser
                                });

                                a.push({
                                    titleKey : 'banner.userActions.createUser',
                                    action : $scope.goCreateUser
                                });

                            }
                            else {

                                a.push({
                                    titleKey : 'banner.userActions.logout',
                                    action : $scope.goLogoutUser
                                });

                                a.push({
                                    titleKey : 'banner.userActions.details',
                                    action : $scope.goViewUser
                                });

                            }

                            $scope.userActions = a;
                        }
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
                        $location.path('/authenticateuser').search({ destination: p });
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

                    refreshUserActions();

                }
            ]
    };
});