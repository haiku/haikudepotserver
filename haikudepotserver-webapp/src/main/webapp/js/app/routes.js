/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').config(
    [
        '$routeProvider',
        function($routeProvider) {
            $routeProvider
                .when('/runtimeinformation',{controller:'RuntimeInformationController', templateUrl:'/js/app/controller/runtimeinformation.html'})
                .when('/more',{controller:'MoreController', templateUrl:'/js/app/controller/more.html'})
                .when('/authenticateuser',{controller:'AuthenticateUserController', templateUrl:'/js/app/controller/authenticateuser.html'})
                .when('/createuser',{controller:'CreateUserController', templateUrl:'/js/app/controller/createuser.html'})
                .when('/changepassword/:nickname',{controller:'ChangePasswordController', templateUrl:'/js/app/controller/changepassword.html'})
                .when('/viewuser/:nickname',{controller:'ViewUserController', templateUrl:'/js/app/controller/viewuser.html'})
                .when('/viewpkg/:name/:version/:architectureCode',{controller:'ViewPkgController', templateUrl:'/js/app/controller/viewpkg.html'})
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
                .otherwise({controller:'OtherwiseController', template: '<div></div>'});
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

/*
This is a controller for an unknown page which just goes through to the error page.
 */

angular.module('haikudepotserver').controller(
    'OtherwiseController',
    [
        '$log','$location',
        'errorHandling',
        function(
            $log,$location,
            errorHandling) {

            errorHandling.handleUnknownLocation();

        }
    ]
);