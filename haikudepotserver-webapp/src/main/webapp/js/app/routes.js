/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').config(
    [
        '$routeProvider',
        function($routeProvider) {
            $routeProvider
                .when('/authenticateuser',{controller:'AuthenticateUserController', templateUrl:'/js/app/controller/authenticateuser.html'})
                .when('/createuser',{controller:'CreateUserController', templateUrl:'/js/app/controller/createuser.html'})
                .when('/viewuser/:nickname',{controller:'ViewUserController', templateUrl:'/js/app/controller/viewuser.html'})
                .when('/viewpkg/:name/:version',{controller:'ViewPkgController', templateUrl:'/js/app/controller/viewpkg.html'})
                .when('/editpkgicon/:name',{controller:'EditPkgIconController', templateUrl:'/js/app/controller/editpkgicon.html'})
                .when('/error',{controller:'ErrorController', templateUrl:'/js/app/controller/error.html'})
                .when('/',{controller:'HomeController', templateUrl:'/js/app/controller/home.html'})
                .otherwise({redirectTo:'/'});
        }
    ]
);