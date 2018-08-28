/*
 * Copyright 2014-2018, Andrew Lindesay
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
             * @param {string} pkgName
             * @param {string} repositoryCode
             * @param {Object} versionCoordinates
             * @param {string} architectureCode
             */

            function generateBaseUrlForPkg(pkgName,repositoryCode,versionCoordinates,architectureCode) {
                if(!pkgName||!pkgName.length) {
                    throw Error('the package name must be supplied');
                }

                if(!repositoryCode||!repositoryCode.length) {
                    throw Error('the repository code must be supplied');
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
                    repositoryCode,
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

            function createViewPkgBreadcrumbItem(pkgName,pkgTitle,repositoryCode,versionCoordinates,architectureCode) {
                return applyDefaults({
                    titleKey : 'breadcrumb.viewPkg.title',
                    titleParameters : [ pkgTitle||pkgName ],
                    path : generateBaseUrlForPkg(pkgName,repositoryCode,versionCoordinates,architectureCode)
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
                        pkgVersion.repositoryCode,
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

            function toFullPath(item, query) {

                // this function can be used in a "reduce" in order to apply the query parameters (search)
                // so they can be added to a path of a URL or to the AngularJS route path.

                function reduceIterator(memo, key) {
                    if('bcguid'==key) {
                        return memo;
                    }
                    else {
                        return memo +
                            ((-1 == memo.indexOf('?')) ? '?' : '&') +
                            encodeURI(key) + '=' + encodeURI(this[key]); // this = context of the reduce
                    }
                }

                // this is a bit hard to read, but it is applying the query to the main part of the path
                // and the search from the 'item' to the AngularJS part of the path.

                return _.reduce(
                    item.search ? _.keys(item.search) : [],
                    reduceIterator,
                    _.reduce(
                        query ? _.keys(query) : [],
                        reduceIterator,
                        window.location.pathname,
                        query
                    ) + '#!' + item.path,
                    item.search
                );

            }

            return {

                // -----------------
                // MANIPULATE BREADCRUMB ITEMS

                /**
                 * <p>The AngularJS route path might be something like "/viewfoo", but to be a full hyperlink this
                 * would need to be something like "/#/viewfoo".  This function will return a full hyperlink path for
                 * the route path of this item.  It will include the item's 'search' on the AngularJS portion of the
                 * path (after the hash).  The optionally supplied 'query' will be applied to the root path (before
                 * the hash) and will therefore have an influence on the JSP page that generates the 'substrate' for
                 * the AngularJS application.</p>
                 */

                toFullPath: function(item, query) {
                    return toFullPath(item, query);
                },

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

                createHome : function(search) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.home.title',
                        path : '/',
                        search : search
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

                createEditRepositorySource : function(repositorySource) {
                    if(!repositorySource) {
                        throw Error('a repository source must be provided');
                    }

                    var repositoryCode = repositorySource.repository ? repositorySource.repository.code : repositorySource.repositoryCode;

                    if(!repositoryCode) {
                        throw Error('a repository code must be able to be supplied from a repository source');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.editRepositorySource.title',
                        path : '/repository/' + repositoryCode + '/source/' + repositorySource.code + '/edit'
                    });
                },

                createEditRepositorySourceMirror : function(repositorySourceMirror) {
                    if(!repositorySourceMirror) {
                        throw Error('a repository source mirror must be provided');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.editRepositorySourceMirror.title',
                        path : '/repository/' +
                            repositorySourceMirror.repositorySource.repository.code +
                            '/source/' + repositorySourceMirror.repositorySource.code +
                            '/mirror/' + repositorySourceMirror.code +
                            '/edit'
                    });
                },

                createEditRepository : function(repository) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.editRepository.title',
                        titleParameters : [ repository.code ],
                        path : '/repository/' + repository.code + '/edit'
                    });
                },

                createAddRepositorySource : function(repository) {
                    if(!repository) {
                        throw Error('a repository needs to be supplied in order to add a repository source');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.addRepositorySource.title',
                        path : '/repository/' + repository.code + '/sources/add'
                    });
                },

                createAddRepositorySourceMirror : function(repositorySource) {
                    if(!repositorySource) {
                        throw Error('a repository source needs to be supplied in order to add a repository source mirror');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.addRepositorySourceMirror.title',
                        path : '/repository/' + repositorySource.repository.code +
                            '/source/' + repositorySource.code + '/mirrors/add'
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
                        path : '/pkg/'+pkg.name
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

                createViewPkgChangelog : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'viewchangelog', 'viewPkgChangelog');
                },

                createEditPkgChangelog : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editchangelog', 'editPkgChangelog');
                },

                createEditPkgLocalization : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editlocalizations', 'editPkgLocalizations');
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

                createViewRepositorySource : function(repositorySource) {
                    if(!repositorySource) {
                        throw Error('a repository source must be provided');
                    }

                    var repositoryCode = repositorySource.repository ? repositorySource.repository.code : repositorySource.repositoryCode;

                    if(!repositoryCode) {
                        throw Error('a repository code must be able to be supplied from a repository source');
                    }

                    return applyDefaults({
                        titleKey : 'breadcrumb.viewRepositorySource.title',
                        path : '/repository/' + repositoryCode + '/source/' + repositorySource.code
                    });
                },

                createViewRepositorySourceMirror : function(repositorySourceMirror) {
                    if(!repositorySourceMirror) {
                        throw Error('a repository source mirror must be provided');
                    }

                    var repositoryCode = repositorySourceMirror.repositorySource.repository.code;
                    var repositorySourceCode = repositorySourceMirror.repositorySource.code;

                    return applyDefaults({
                        titleKey : 'breadcrumb.viewRepositorySourceMirror.title',
                        path : '/repository/' + repositoryCode +
                            '/source/' + repositorySourceCode +
                            "/mirror/" + repositorySourceMirror.code
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
                        pkgVersion.title,
                        pkgVersion.repositoryCode,
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
                        pkgVersion.title,
                        pkgVersion.repositoryCode,
                        pkgVersion,
                        pkgVersion.architectureCode);
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

                createPaginationPlayground : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.paginationPlayground.title',
                        path : '/paginationcontrolplayground'
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
                },

                createPkgIconArchiveImport : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.pkgIconArchiveImport.title',
                        path : '/pkgiconarchiveimport'
                    });
                },

                createPkgScreenshotArchiveImport : function() {
                    return applyDefaults({
                        titleKey : 'breadcrumb.pkgScreenshotArchiveImport.title',
                        path : '/pkgscreenshotarchiveimport'
                    });
                }

            };

        }
    ]
);