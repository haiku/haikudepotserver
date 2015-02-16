/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').config(
    [
        '$routeProvider',
        function($routeProvider) {
            // eg; '/pkg/apr/1/4/6/3/7/x86'
            var pkgVersionPrefix = '/pkg/:name/:major/:minor?/:micro?/:preRelease?/:revision?/:architectureCode';

            $routeProvider
                .when('/rootoperations',{controller:'RootOperationsController',templateUrl:'/js/app/controller/rootoperations.html'})
                .when('/jobs',{controller:'ListJobsController',templateUrl:'/js/app/controller/listjobs.html'})
                .when('/pkgcategorycoverageimportspreadsheet',{controller:'PkgCategoryCoverageImportSpreadsheetController',templateUrl:'/js/app/controller/pkgcategorycoverageimportspreadsheet.html'})
                .when('/job/:guid',{controller:'ViewJobController',templateUrl:'/js/app/controller/viewjob.html'})
                .when('/reports',{controller:'ReportsController',templateUrl:'/js/app/controller/reports.html'})
                .when('/pkg/feed/builder',{controller:'PkgFeedBuilderController',templateUrl:'/js/app/controller/pkgfeedbuilder.html'})
                .when('/paginationcontrolplayground',{controller:'PaginationControlPlayground',templateUrl:'/js/app/controller/paginationcontrolplayground.html'})
                .when('/repositories/add',{controller:'AddEditRepositoryController',templateUrl:'/js/app/controller/addeditrepository.html'})
                .when('/repository/:code/edit',{controller:'AddEditRepositoryController',templateUrl:'/js/app/controller/addeditrepository.html'})
                .when('/repository/:code',{controller:'ViewRepositoryController', templateUrl:'/js/app/controller/viewrepository.html'})
                .when('/repositories',{controller:'ListRepositoriesController', templateUrl:'/js/app/controller/listrepositories.html'})
                .when('/runtimeinformation',{controller:'RuntimeInformationController', templateUrl:'/js/app/controller/runtimeinformation.html'})
                .when('/about',{controller:'AboutController', templateUrl:'/js/app/controller/about.html'})
                .when('/authenticateuser',{controller:'AuthenticateUserController', templateUrl:'/js/app/controller/authenticateuser.html'})
                .when('/initiatepasswordreset',{controller:'InitiatePasswordResetController', templateUrl:'/js/app/controller/initiatepasswordreset.html'})
                .when('/completepasswordreset/:token',{controller:'CompletePasswordResetController', templateUrl:'/js/app/controller/completepasswordreset.html'})
                .when('/authorizationpkgrules',{controller:'ListAuthorizationPkgRulesController', templateUrl:'/js/app/controller/listauthorizationpkgrules.html'})
                .when('/authorizationpkgrules/add',{controller:'AddAuthorizationPkgRuleController', templateUrl:'/js/app/controller/addauthorizationpkgrule.html'})
                .when('/users',{controller:'ListUsersController', templateUrl:'/js/app/controller/listusers.html'})
                .when('/users/add',{controller:'CreateUserController', templateUrl:'/js/app/controller/createuser.html'})
                .when('/user/:nickname',{controller:'ViewUserController', templateUrl:'/js/app/controller/viewuser.html'})
                .when('/user/:nickname/edit',{controller:'EditUserController', templateUrl:'/js/app/controller/edituser.html'})
                .when('/user/:nickname/changepassword',{controller:'ChangePasswordController', templateUrl:'/js/app/controller/changepassword.html'})
                .when('/user/:nickname/jobs',{controller:'ListJobsController', templateUrl:'/js/app/controller/listjobs.html'})
                .when('/userrating/:code/edit',{controller:'AddEditUserRatingController', templateUrl:'/js/app/controller/addedituserrating.html'})
                .when('/userrating/:code',{controller:'ViewUserRatingController', templateUrl:'/js/app/controller/viewuserrating.html'})
                .when('/pkg/:name/listpkgversions',{controller:'ListPkgVersionsForPkgController', templateUrl:'/js/app/controller/listpkgversionsforpkg.html'})
                .when(pkgVersionPrefix,{controller:'ViewPkgController', templateUrl:'/js/app/controller/viewpkg.html'})
                .when(pkgVersionPrefix+'/editicon',{controller:'EditPkgIconController', templateUrl:'/js/app/controller/editpkgicon.html'})
                .when(pkgVersionPrefix+'/editscreenshots',{controller:'EditPkgScreenshotsController', templateUrl:'/js/app/controller/editpkgscreenshots.html'})
                .when(pkgVersionPrefix+'/editcategories',{controller:'EditPkgCategoriesController', templateUrl:'/js/app/controller/editpkgcategories.html'})
                .when(pkgVersionPrefix+'/editprominence',{controller:'EditPkgProminenceController', templateUrl:'/js/app/controller/editpkgprominence.html'})
                .when(pkgVersionPrefix+'/viewversionlocalizations',{controller:'ViewPkgVersionLocalizationController', templateUrl:'/js/app/controller/viewpkgversionlocalization.html'})
                .when(pkgVersionPrefix+'/editversionlocalizations',{controller:'EditPkgVersionLocalizationController', templateUrl:'/js/app/controller/editpkgversionlocalization.html'})
                .when(pkgVersionPrefix+'/adduserrating',{controller:'AddEditUserRatingController', templateUrl:'/js/app/controller/addedituserrating.html'})
                .when('/',{controller:'HomeController',templateUrl:'/js/app/controller/home.html',reloadOnSearch:false})
                .otherwise({controller:'OtherwiseController', template: '<div></div>'});
        }
    ]
);

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