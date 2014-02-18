/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service helps in the creation of breadcrumb items.</p>
 */

angular.module('haikudepotserver').factory('breadcrumbs',
    [
        function() {

            var BreadcrumbsService = {

                createEditRepository : function(repository) {
                    return {
                        title : 'Edit ' + repository.code,
                        path : '/editrepository/' + repository.code
                    }
                },

                createAddRepository : function(repository) {
                    return {
                        title : 'Add Repository',
                        path : '/addrepository'
                    }
                },

                createViewRepository : function(repository) {
                  return {
                      title : repository.code,
                      path : '/viewrepository/' + repository.code
                  }
                },

                createListRepositories : function() {
                    return {
                        title : 'List Repositories',
                        path : '/listrepositories'
                    };
                },

                createRuntimeInformation : function() {
                    return {
                        title : 'Runtime Information',
                        path : '/runtimeinformation'
                    };
                },

                createMore : function() {
                  return {
                      title : 'More',
                      path : '/more'
                  };
                },

                createViewPkg : function(pkg,versionType,architectureCode) {

                    if(!pkg||!pkg.name||!pkg.name.length) {
                        throw 'the package must be supplied to create a view pkg and must have a name';
                    }

                    if(!versionType||!versionType.length) {
                        throw 'the versionType must be supplied to create a view pkg';
                    }

                    if(!architectureCode||!architectureCode.length) {
                        throw 'the architectureCode must be supplied to create a view pkg';
                    }

                    return {
                        title : pkg.name,
                        path : '/viewpkg/' + pkg.name + '/' + versionType + '/' + architectureCode
                    };
                },

                createEditPkgIcon : function(pkg) {
                    return {
                        title : 'Edit Icon',
                        path : '/editpkgicon/' + pkg.name
                    };
                },

                createEditPkgScreenshots : function(pkg) {
                    return {
                        title : 'Screenshots', // TODO - localize
                            path : '/editpkgscreenshots/'+pkg.name
                    };
                },

                createViewUser : function(user) {
                    return {
                        title : user.nickname,
                        path : '/viewuser/' + user.nickname
                    };
                },

                createChangePassword : function(user) {
                    return {
                        title : 'Change Password',
                        path : '/changepassword/' + user.nickname
                    };
                }
            };

            return BreadcrumbsService;

        }
    ]
);