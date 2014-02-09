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