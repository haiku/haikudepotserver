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
                '$scope','$log','$location','$route',
                'userState','referenceData',
                function(
                    $scope,$log,$location,$route,
                    userState,referenceData) {

                    $scope.architectureOptions;
                    $scope.architectures;
                    $scope.architecture;

                    referenceData.architectures().then(
                        function(architectures) {
                            $scope.architectures = architectures;
                        },
                        function() {
                            $log.error('a problem has arisen obtaining the architectures');
                            $location.path("/error").search({});
                        }
                    );

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

                    refreshArchitecture();

                    $scope.isArchitectureSelected = function(a) {
                        return $scope.architecture && $scope.architecture.code == a.code;
                    }

                    $scope.canCreateUser = function() {
                        return !userState.user() && $location.path() != '/createuser';
                    }

                    $scope.canAuthenticateUser = function() {
                        return !userState.user() && $location.path() != '/authenticateuser';
                    }

                    $scope.canLogoutUser = function() {
                        return userState.user();
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

                    $scope.goHome = function() {
                        $location.path('/').search({});
                        return false;
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

                    $scope.userDisplayTitle = function() {
                        var data = userState.user();
                        return data ? data.nickname : undefined;
                    }

                }
            ]
    };
});