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
                .when('/',{
                    controller:'HomeController',
                    templateUrl:'/js/app/controller/home.html',
                    resolve: {
                        'architectures':[
                            'referenceData', function(referenceData) {
                                return referenceData.architectures();
                            }
                        ]
                    }
                })
                .otherwise({redirectTo:'/'});
        }
    ]
);

/*
 Preload the error template because if the system is not able to access the server then it will have an
 error, but may not be able to get access to the error template to show the error page!
 */

angular.module('haikudepotserver').run(function($http,$templateCache) {
    $http.get('/js/app/controller/error.html', { cache: $templateCache });
});