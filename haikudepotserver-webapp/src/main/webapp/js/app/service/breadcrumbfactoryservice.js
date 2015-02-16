/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service helps in the creation of breadcrumb items for use with the breadcrumb service.</p>
 */

angular.module('haikudepotserver').factory('breadcrumbFactory',
    [
        '$rootScope','$location',
        function($rootScope,$location) {

            /**
             * <p>Many of the URLs related to packages stem from the same base URL.  This function
             * will create that base URL.</p>
             */

            function generateBaseUrlForPkg(pkgName,versionCoordinates,architectureCode) {
                if(!pkgName||!pkgName.length) {
                    throw Error('the package name must be supplied');
                }

                if(!versionCoordinates||!versionCoordinates.major) {
                    throw Error('version coordinates must be supplied');
                }

                if(!architectureCode||!architectureCode.length) {
                    throw Error('the architectureCode must be supplied to create a view pkg');
                }

                var parts = [
                    'pkg',
                    pkgName,
                    versionCoordinates.major,
                    versionCoordinates.minor ? versionCoordinates.minor : '-',
                    versionCoordinates.micro ? versionCoordinates.micro : '-',
                    versionCoordinates.preRelease ? versionCoordinates.preRelease : '-',
                    versionCoordinates.revision ? versionCoordinates.revision : '-',
                    architectureCode
                ];

                return '/' + parts.join('/');
            }

            /**
             * <p>The creation of a view pkg breadcrumb is a bit complex.  This function will take care of it
             * based on the details provided.</p>
             */

            function createViewPkgBreadcrumbItem(pkgName,versionCoordinates,architectureCode) {
                return applyDefaults({
                    titleKey : 'breadcrumb.viewPkg.title',
                    titleParameters : [ pkgName ],
                    path : generateBaseUrlForPkg(pkgName,versionCoordinates,architectureCode)
                });
            }

            /**
             * <p>From a list of pkg versions, tries to find the one that is identified as the
             * latest.  If this is not possible then it will take the first one.</p>
             */

            function latestVersion(pkgVersions) {
                if(!pkgVersions || !pkgVersions.length) {
                    throw Error('a package version is required to get the latest');
                }

                var pkgVersion = _.findWhere(pkgVersions, { isLatest : true } );

                if(!pkgVersion) {
                    pkgVersion = pkgVersions[0];
                }

                return pkgVersion;
            }

            function createManipulatePkgBreadcrumbItem(pkgWithVersion0, pathSuffix, titlePortion) {
                if(!pkgWithVersion0 || !pkgWithVersion0.versions || !pkgWithVersion0.versions.length) {
                    throw Error('a package version is required to form a breadcrumb');
                }

                var pkgVersion = latestVersion(pkgWithVersion0.versions);

                return applyDefaults({
                    titleKey : 'breadcrumb.'+titlePortion+'.title',
                    path : generateBaseUrlForPkg(
                        pkgWithVersion0.name,
                        pkgVersion,
                        pkgVersion.architectureCode) + '/' + pathSuffix
                });
            }

            /**
             * <p>When a new breadcrumb item is created, it should have a unique identifier in order that the user
             * using a back-button is able to re-visit the URL and the system knows that actually the thing that
             * we're going back to it is actually a prior value in the breadcrumbs and not a new URL being visited.
             * </p>
             */

            function applyDefaults(item) {

                function randomChars(acc, length) {
                    var abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
                    return 0==length ? acc : randomChars(acc + abc[Math.floor(Math.random() * (abc.length-1))], length-1);
                }

                var guid = _.uniqueId('bc') + '-' + randomChars('',4);

                if(item.search) {
                    item.search.bcguid = guid;
                }
                else {
                    item.search = { bcguid : guid };
                }

                return item;
            }

            /**
             * <p>This will configure a breadcrumb item with the current location.  This will take-up the search
             * and path so that it can be regurgitated later.</p>
             */

            function applyCurrentLocation(item) {
                if(!item) {
                    throw Error('was expecting an item to be supplied to be augmented');
                }

                item.path = $location.path();
                item.search = _.extend(item.search ? item.search : {}, $location.search());

                return item;
            }

            return {

                // -----------------
                // MANIPULATE BREADCRUMB ITEMS

                /**
                 * <p>This function will blend in the current location (path and search) to the supplied item.  This
                 * is useful to pickup search items that were already supplied on the path.  The item is
                 * returned to provide for chained construction.</p>
                 */

                applyCurrentLocation: function(item) {
                    return applyCurrentLocation(item);
                },

                /**
                 * <p>This function will blend in the supplied search data into the item.</p>
                 */

                applySearch: function(item, search) {
                    if(!item.search) {
                        item.search = {};
                    }

                    item.search = _.extend(item.search,search);
                    return item;
                },

                // -----------------
                // CREATE STANDARD BREADCRUMB ITEMS

                createHome : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.home.title',
                        path : '/'
                    });
                },

                createInitiatePasswordReset : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.initiatePasswordReset.title',
                        path : '/initiatepasswordreset'
                    });
                },

                createCompletePasswordReset : function(token) {
                    if(!token||!token.length) {
                        throw Error('unable to create complete password reset without a token');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.completePasswordReset.title',
                        path : '/completepasswordreset/'+token
                    });
                },

                createAuthenticate : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.authenticateUser.title',
                        path : '/authenticateuser'
                    });
                },

                createAddUser : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.createUser.title',
                        path : '/users/add'
                    });
                },

                createEditUser : function(user) {
                    if(!user||!user.nickname) {
                        throw Error('user with nickname is required to make this breadcrumb');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.editUser.title',
                        path : '/user/'+user.nickname+'/edit'
                    });
                },

                createEditRepository : function(repository) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.editRepository.title',
                        titleParameters : [ repository.code ],
                        path : '/repository/' + repository.code + '/edit'
                    });
                },

                createAddRepository : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.addRepository.title',
                        path : '/repositories/add'
                    });
                },

                createEditUserRating : function(userRating) {
                    if(!userRating || !userRating.code) {
                        throw Error('a user rating code is expected');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.editUserRating.title',
                        path : '/userrating/'+userRating.code+'/edit'
                    });
                },

                createListPkgVersionsForPkg : function(pkg) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.listPkgVersionsForPkg.title',
                        path : '/pkg/'+pkg.name+'/listpkgversions'
                    });
                },

                createEditPkgCategories : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editcategories', 'editPkgCategories');
                },

                createEditPkgProminence : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editprominence', 'editPkgProminence');
                },

                createEditPkgIcon : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editicon', 'editPkgIcon');
                },

                createEditPkgScreenshots : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editscreenshots', 'editPkgScreenshots');
                },

                createViewPkgVersionLocalization : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'viewversionlocalizations', 'viewPkgVersionLocalizations');
                },

                createEditPkgVersionLocalization : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editversionlocalizations', 'editPkgVersionLocalizations');
                },

                createAddUserRating : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'adduserrating', 'addUserRating');
                },

                createViewUserRating : function(userRating) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.viewUserRating.title',
                        path : '/userrating/' + userRating.code
                    });
                },

                createViewRepository : function(repository) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.viewRepository.title',
                        titleParameters : repository.code,
                        path : '/repository/' + repository.code
                    });
                },

                createListRepositories : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.listRepositories.title',
                        path : '/repositories'
                    });
                },

                createRuntimeInformation : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.runtimeInformation.title',
                        path : '/runtimeinformation'
                    });
                },

                createAbout : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.about.title',
                        path : '/about'
                    });
                },

                /**
                 * <p>This function will take the package version (containing the package) and will
                 * return the breadcrumb for it.</p>
                 */

                createViewPkgWithSpecificVersionFromPkgVersion : function(pkgVersion) {
                    if(!pkgVersion || !pkgVersion.pkg) {
                        throw Error('a package version is required to form a breadcrumb');
                    }

                    return createViewPkgBreadcrumbItem(
                        pkgVersion.pkg.name,
                        pkgVersion,
                        pkgVersion.architectureCode);
                },

                /**
                 * <p>This function will return a breadcrumb for viewing a specific package.  It expects
                 * a data structure similar to the API return data from "GetPkgResult" where only the
                 * first version is considered.</p>
                 */

                createViewPkgWithSpecificVersionFromPkg : function(pkg) {
                    if(!pkg || !pkg.versions.length) {
                        throw Error('a package with a package version are required to form a breadcrumb');
                    }

                    var pkgVersion = latestVersion(pkg.versions);

                    return createViewPkgBreadcrumbItem(
                        pkg.name,
                        pkgVersion,
                        pkgVersion.architectureCode);
                },

                createViewPkgWithSpecificVersionFromRouteParams : function(routeParams) {

                    if(!routeParams||!routeParams.major) {
                        throw Error('route params are expected');
                    }

                    return createViewPkgBreadcrumbItem(
                        routeParams.name,
                        {
                            major : routeParams.major,
                            minor : routeParams.minor,
                            micro : routeParams.micro,
                            preRelease : routeParams.preRelease,
                            revision : routeParams.revision
                        },
                        routeParams.architectureCode);
                },

                createViewUser : function(user) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.viewUser.title',
                        titleParameters : [ user.nickname ],
                        path : '/user/' + user.nickname
                    });
                },

                createListUsers : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.listUsers.title',
                        path : '/users/'
                    });
                },

                createChangePassword : function(user) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.changePassword.title',
                        path : '/user/' + user.nickname + '/changepassword'
                    });
                },

                createListAuthorizationPkgRules : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.listAuthorizationPkgRules.title',
                        path : '/authorizationpkgrules'
                    });
                },

                createAddAuthorizationPkgRule : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.addAuthorizationPkgRule.title',
                        path : '/authorizationpkgrules/add'
                    });
                },

                createPkgFeedBuilder : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.pkgFeedBuilder.title',
                        path : '/pkg/feed/builder'
                    });
                },

                createRootOperations : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.rootOperations.title',
                        path : '/rootoperations'
                    });
                },

                createReports : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.reports.title',
                        path : '/reports'
                    });
                },

                createListJobs : function(user) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.listJobs.title',
                        path : user ? '/user/' + user.nickname + '/jobs' : '/jobs'
                    });
                },

                createViewJob : function(job) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.viewJob.title',
                        path : '/job/' + job.guid
                    });
                },

                createPkgCategoryCoverageImportSpreadsheet : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.pkgCategoryCoverageImportSpreadsheet.title',
                        path : '/pkgcategorycoverageimportspreadsheet'
                    });
                }

            };

        }
    ]
);