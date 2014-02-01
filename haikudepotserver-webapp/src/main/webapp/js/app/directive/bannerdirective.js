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

                    $scope.actions = [];
                    refreshActions(); // not direct assignment in case it later has to be a promise.

                    // when the page changes, the actions may change; for example, it is not appropriate to
                    // show the 'login' option when the user is presently logging in.

                    $rootScope.$on(
                        "$locationChangeSuccess",
                        function(event, next, current) {
                            refreshActions();
                        }
                    );

                    // when the user logs in or out then the actions may also change; for example, it makes
                    // no sense to show the logout button if nobody is presently logged in.

                    $rootScope.$on(
                        "userChangeSuccess",
                        function(event, next, current) {
                            refreshActions();
                        }
                    );

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        $location.path('/').search({});
                        return false;
                    }

                    function canGoMore() {
                        var p = $location.path();
                        return '/error' != p && '/more' != p;
                    }

                    function isLocationPathDisablingUserState() {
                        var p = $location.path();
                        return '/error' == p || '/authenticateuser' == p || '/createuser' == p;
                    }

                    function canAuthenticateOrCreate() {
                        return !isLocationPathDisablingUserState() && !userState.user();
                    }

                    function canShowAuthenticated() {
                        return !isLocationPathDisablingUserState() && userState.user()
                    }

                    function refreshActions() {
                        var a = [];

                        if(canGoMore()) {
                            a.push({
                                title : 'more',
                                action: function() {
                                    $location.path('/more').search({});
                                }
                            });
                        }

                        if(canAuthenticateOrCreate()) {
                            a.push({
                                title : 'login',
                                action: function() {
                                    var p = $location.path();
                                    $location.path('/authenticateuser').search(
                                        _.extend($location.search(), { destination: p }));
                                }
                            });

                            a.push({
                                title : 'register',
                                action: function() {
                                    $location.path('/createuser').search({});
                                }
                            });
                        }

                        if(canShowAuthenticated()) {

                            a.push({
                                title : userState.user().nickname,
                                action: function() {
                                    $location.path('/viewuser/'+userState.user().nickname).search({});
                                }
                            });

                            a.push({
                                title : 'logout',
                                action: function() {
                                    userState.user(null);
                                    $location.path('/').search({});
                                }
                            });

                        }

                        $scope.actions = a;
                    }

                }
            ]
    };
});