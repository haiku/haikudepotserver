/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').config(
    [
        '$routeProvider',
        function($routeProvider) {
            // eg; '/pkg/apr/1/4/6/3/7/x86'
            var pkgVersionPrefix = '/pkg/:name/:repositoryCode/:major/:minor?/:micro?/:preRelease?/:revision?/:architectureCode';
            var controllerTemplatePathPrefix = '/__js/app/controller';

            $routeProvider
                .when('/rootoperations',{controller:'RootOperationsController',templateUrl:controllerTemplatePathPrefix + '/rootoperations.html'})
                .when('/jobs',{controller:'ListJobsController',templateUrl:controllerTemplatePathPrefix + '/listjobs.html'})
                .when('/pkgcategorycoverageimportspreadsheet',{controller:'PkgCategoryCoverageImportSpreadsheetController',templateUrl:controllerTemplatePathPrefix + '/pkgcategorycoverageimportspreadsheet.html'})
                .when('/job/:guid',{controller:'ViewJobController',templateUrl:controllerTemplatePathPrefix + '/viewjob.html'})
                .when('/reports',{controller:'ReportsController',templateUrl:controllerTemplatePathPrefix + '/reports.html'})
                .when('/pkg/feed/builder',{controller:'PkgFeedBuilderController',templateUrl:controllerTemplatePathPrefix + '/pkgfeedbuilder.html'})
                .when('/paginationcontrolplayground',{controller:'PaginationControlPlayground',templateUrl:controllerTemplatePathPrefix + '/paginationcontrolplayground.html'})
                .when('/repositories/add',{controller:'AddEditRepositoryController',templateUrl:controllerTemplatePathPrefix + '/addeditrepository.html'})
                .when('/repository/:repositoryCode/sources/add',{controller:'AddEditRepositorySourceController',templateUrl:controllerTemplatePathPrefix + '/addeditrepositorysource.html'})
                .when('/repository/:repositoryCode/source/:repositorySourceCode/edit',{controller:'AddEditRepositorySourceController',templateUrl:controllerTemplatePathPrefix + '/addeditrepositorysource.html'})
                .when('/repository/:repositoryCode/source/:repositorySourceCode',{controller:'ViewRepositorySourceController',templateUrl:controllerTemplatePathPrefix + '/viewrepositorysource.html'})
                .when('/repository/:code/edit',{controller:'AddEditRepositoryController',templateUrl:controllerTemplatePathPrefix + '/addeditrepository.html'})
                .when('/repository/:code',{controller:'ViewRepositoryController', templateUrl:controllerTemplatePathPrefix + '/viewrepository.html'})
                .when('/repositories',{controller:'ListRepositoriesController', templateUrl:controllerTemplatePathPrefix + '/listrepositories.html'})
                .when('/runtimeinformation',{controller:'RuntimeInformationController', templateUrl:controllerTemplatePathPrefix + '/runtimeinformation.html'})
                .when('/about',{controller:'AboutController', templateUrl:controllerTemplatePathPrefix + '/about.html'})
                .when('/authenticateuser',{controller:'AuthenticateUserController', templateUrl:controllerTemplatePathPrefix + '/authenticateuser.html'})
                .when('/initiatepasswordreset',{controller:'InitiatePasswordResetController', templateUrl:controllerTemplatePathPrefix + '/initiatepasswordreset.html'})
                .when('/completepasswordreset/:token',{controller:'CompletePasswordResetController', templateUrl:controllerTemplatePathPrefix + '/completepasswordreset.html'})
                .when('/authorizationpkgrules',{controller:'ListAuthorizationPkgRulesController', templateUrl:controllerTemplatePathPrefix + '/listauthorizationpkgrules.html'})
                .when('/authorizationpkgrules/add',{controller:'AddAuthorizationPkgRuleController', templateUrl:controllerTemplatePathPrefix + '/addauthorizationpkgrule.html'})
                .when('/users',{controller:'ListUsersController', templateUrl:controllerTemplatePathPrefix + '/listusers.html'})
                .when('/users/add',{controller:'CreateUserController', templateUrl:controllerTemplatePathPrefix + '/createuser.html'})
                .when('/user/:nickname',{controller:'ViewUserController', templateUrl:controllerTemplatePathPrefix + '/viewuser.html'})
                .when('/user/:nickname/edit',{controller:'EditUserController', templateUrl:controllerTemplatePathPrefix + '/edituser.html'})
                .when('/user/:nickname/changepassword',{controller:'ChangePasswordController', templateUrl:controllerTemplatePathPrefix + '/changepassword.html'})
                .when('/user/:nickname/jobs',{controller:'ListJobsController', templateUrl:controllerTemplatePathPrefix + '/listjobs.html'})
                .when('/userrating/:code/edit',{controller:'AddEditUserRatingController', templateUrl:controllerTemplatePathPrefix + '/addedituserrating.html'})
                .when('/userrating/:code',{controller:'ViewUserRatingController', templateUrl:controllerTemplatePathPrefix + '/viewuserrating.html'})
                .when('/pkg/:name',{controller:'ListPkgVersionsForPkgController', templateUrl:controllerTemplatePathPrefix + '/listpkgversionsforpkg.html'})
                .when(pkgVersionPrefix,{controller:'ViewPkgController', templateUrl:controllerTemplatePathPrefix + '/viewpkg.html'})
                .when(pkgVersionPrefix+'/editicon',{controller:'EditPkgIconController', templateUrl:controllerTemplatePathPrefix + '/editpkgicon.html'})
                .when(pkgVersionPrefix+'/editscreenshots',{controller:'EditPkgScreenshotsController', templateUrl:controllerTemplatePathPrefix + '/editpkgscreenshots.html'})
                .when(pkgVersionPrefix+'/editcategories',{controller:'EditPkgCategoriesController', templateUrl:controllerTemplatePathPrefix + '/editpkgcategories.html'})
                .when(pkgVersionPrefix+'/editprominence',{controller:'EditPkgProminenceController', templateUrl:controllerTemplatePathPrefix + '/editpkgprominence.html'})
                .when(pkgVersionPrefix+'/viewversionlocalizations',{controller:'ViewPkgVersionLocalizationController', templateUrl:controllerTemplatePathPrefix + '/viewpkgversionlocalization.html'})
                .when(pkgVersionPrefix+'/viewchangelog',{controller:'ViewPkgChangelogController', templateUrl:controllerTemplatePathPrefix + '/viewpkgchangelog.html'})
                .when(pkgVersionPrefix+'/editchangelog',{controller:'EditPkgChangelogController', templateUrl:controllerTemplatePathPrefix + '/editpkgchangelog.html'})
                .when(pkgVersionPrefix+'/editlocalizations',{controller:'EditPkgLocalizationController', templateUrl:controllerTemplatePathPrefix + '/editpkglocalization.html'})
                .when(pkgVersionPrefix+'/adduserrating',{controller:'AddEditUserRatingController', templateUrl:controllerTemplatePathPrefix + '/addedituserrating.html'})
                .when('/',{controller:'HomeController',templateUrl:controllerTemplatePathPrefix + '/home.html',reloadOnSearch:false})
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