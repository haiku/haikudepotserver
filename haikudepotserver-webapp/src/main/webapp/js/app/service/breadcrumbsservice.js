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

            return {

                createEditRepository : function(repository) {
                    return {
                        title : 'Edit ' + repository.code,
                        path : '/repository/' + repository.code + '/edit'
                    }
                },

                createAddRepository : function() {
                    return {
                        title : 'Add Repository',
                        path : '/repositories/add'
                    }
                },

                createViewRepository : function(repository) {
                  return {
                      title : repository.code,
                      path : '/repository/' + repository.code
                  }
                },

                createListRepositories : function() {
                    return {
                        title : 'List Repositories',
                        path : '/repositories'
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
                      title : 'About',
                      path : '/about'
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
                        path : '/pkg/' + pkg.name + '/' + versionType + '/' + architectureCode
                    };
                },

                createViewUser : function(user) {
                    return {
                        title : user.nickname,
                        path : '/user/' + user.nickname
                    };
                },

                createChangePassword : function(user) {
                    return {
                        title : 'Change Password',
                        path : '/user/' + user.nickname + '/changepassword'
                    };
                }
            };

        }
    ]
);