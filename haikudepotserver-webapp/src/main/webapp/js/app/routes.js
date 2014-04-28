/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').config(
    [
        '$routeProvider',
        function($routeProvider) {
            // eg; '/pkg/apr/1/4/6/3/7/x86'
            var pkgVersionPrefix = '/pkg/:name/:major/:minor?/:micro?/:preRelease?/:revision?/:architectureCode';

            $routeProvider
                .when('/repositories/add',{controller:'AddEditRepositoryController',templateUrl:'/js/app/controller/addeditrepository.html'})
                .when('/repository/:code/edit',{controller:'AddEditRepositoryController',templateUrl:'/js/app/controller/addeditrepository.html'})
                .when('/repository/:code',{controller:'ViewRepositoryController', templateUrl:'/js/app/controller/viewrepository.html'})
                .when('/repositories',{controller:'ListRepositoriesController', templateUrl:'/js/app/controller/listrepositories.html'})
                .when('/runtimeinformation',{controller:'RuntimeInformationController', templateUrl:'/js/app/controller/runtimeinformation.html'})
                .when('/about',{controller:'AboutController', templateUrl:'/js/app/controller/about.html'})
                .when('/authenticateuser',{controller:'AuthenticateUserController', templateUrl:'/js/app/controller/authenticateuser.html'})
                .when('/users/add',{controller:'CreateUserController', templateUrl:'/js/app/controller/createuser.html'})
                .when('/user/:nickname',{controller:'ViewUserController', templateUrl:'/js/app/controller/viewuser.html'})
                .when('/user/:nickname/edit',{controller:'EditUserController', templateUrl:'/js/app/controller/edituser.html'})
                .when('/user/:nickname/changepassword',{controller:'ChangePasswordController', templateUrl:'/js/app/controller/changepassword.html'})
                .when(pkgVersionPrefix,{controller:'ViewPkgController', templateUrl:'/js/app/controller/viewpkg.html'})
                .when(pkgVersionPrefix+'/editicon',{controller:'EditPkgIconController', templateUrl:'/js/app/controller/editpkgicon.html'})
                .when(pkgVersionPrefix+'/editscreenshots',{controller:'EditPkgScreenshotsController', templateUrl:'/js/app/controller/editpkgscreenshots.html'})
                .when(pkgVersionPrefix+'/editcategories',{controller:'EditPkgCategoriesController', templateUrl:'/js/app/controller/editpkgcategories.html'})
                .when(pkgVersionPrefix+'/editversionlocalizations',{controller:'EditPkgVersionLocalizationController', templateUrl:'/js/app/controller/editpkgversionlocalization.html'})
                .when('/error',{controller:'ErrorController', templateUrl:'/js/app/controller/error.html'})
                .when('/',{controller:'HomeController',templateUrl:'/js/app/controller/home.html'})
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