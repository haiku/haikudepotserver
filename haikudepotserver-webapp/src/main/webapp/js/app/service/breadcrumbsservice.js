/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service helps in the pushing of breadcrumb items, but also maintains the state of the breadcrumb trail.  An
 * item should have the following properties;</p>
 *
 * <ul>
 *     <li>title -  verbatim string to use as the title of the breadcrumb item.</li>
 *     <li>titleKey - a key into the localization for the title of the breadcrumb item.</li>
 *     <li>titleParameters - an array of objects to replace tokens in the localized title.</li>
 *     <li>path - the path to visit; set into the $location.</li>
 *     <li>search - search data to configure on the $location.</li>
 * </ul>
 */

angular.module('haikudepotserver').factory('breadcrumbs',
    [
        '$rootScope','$location',
        function($rootScope,$location) {

            /**
             * <p>This variable contains the breadcrumb trail.  Inherently, "home" is not on the breadcrumb trail
             * because it is always there.</p>
             * @type {undefined}
             */

            var stack = undefined;

            function verifyItem(item) {
                if(!item) {
                    throw 'a breadcrumb item was expected';
                }

                if(!item.titleKey || !item.titleKey.length) {
                    throw 'a breadcrumb item must have a title key';
                }

                if(!item.path || !item.path.length) {
                    throw 'a breadcrumb item must have a path';
                }
            }

            function verifyItems(items) {

                if(!items) {
                    throw 'breadcrumb items were expected';
                }

                _.each(items, function(item) {
                    verifyItem(item);
                });
            }

            /**
             * <p>This function will return the breadcrumbs to its pristine state.</p>
             */

            function reset(stackIn) {
                stack = undefined;

                if(stackIn) {
                    verifyItems(stackIn);
                    stack = stackIn;
                }

                $rootScope.$broadcast('breadcrumbChangeSuccess',stack);
            }

            /**
             * <p>This function returns the item that is at the top-most of the stack.</p>
             */

            function peek() {
                if(stack&&stack.length) {
                    return stack[stack.length-1];
                }

                return null;
            }

            /**
             * <p>This will push an item to the breadcrumbs.  It is possible to pass null, in which case the
             * item will not be pushed, but the breadcrumbs will, nonetheless, be initialized and will no
             * longer be pristine.</p>
             */

            function push(item) {
                verifyItem(item);
                stack.push(item);
                $rootScope.$broadcast('breadcrumbChangeSuccess',stack);
            }

            /**
             * <p>This function will remove the deepest item from the breadcrumb stack.</p>
             */

            function pop() {
                if(!stack||!stack.length) {
                    throw 'attempt to pop from empty breadcrumb stack';
                }

                var item = stack.pop();
                $rootScope.$broadcast('breadcrumbChangeSuccess',stack);
                return item;
            }

            /**
             * <p>This function will pop items from the stack until it hits the one mentioned and will then leave
             * that item on the stack.</p>
             */

            function popTo(item) {
                if(!stack) {
                    throw 'the breadcrumb stack is empty; not possible to popTo(..)';
                }

                while(stack.length && stack[stack.length-1] != item) {
                    stack.pop();
                }

                if(!stack.length) {
                    throw 'the item requested to popTo was not found on the breadcrumb stack';
                }

                $rootScope.$broadcast('breadcrumbChangeSuccess',stack);

                return stack[stack.length-1];
            }

            /**
             * <p>This function will get the data from the inbound stack and merge it into the existing breadcrumb
             * stack.  It can do this in a couple of different ways;</p>
             *
             * <ul>
             *     <li>
             *         If the stack is not empty and the last item is not already at the end of the stack, then it
             *         will take the last item in the inbound stack and will put it at the end of the stack.
             *     </li>
             *     <li>
             *         If the stack is not empty and the last item in the inbound stack is the same as the one in the
             *         existing stack, then it will merge the data from the inbound stack's top most item into the data
             *         of the existing stack.
             *     </li>
             *     <li>
             *         If the stack is empty (or undefined) then it will just use the inbound stack.
             *     </li>
             *  <ul>
             *
             *  <p>Items are considered to be the same if their location matches.</p>
             */

            function mergeCompleteStack(stackIn) {
                if(!stackIn || !stackIn.length) {
                    throw 'attempt to merge an empty stack into the existing stack; not possible';
                }

                verifyItems(stackIn);

                if(!stack || !stack.length) {
                    stack = stackIn;
                }
                else {
                    if(stack[stack.length-1].path == stackIn[stackIn.length-1].path) {
//                        stack[stack.length-1].path = _.extend(
//                            stack[stack.length-1].data,
//                            stackIn[stackIn.length-1].data);
                    }
                    else {
                        stack.push(stackIn[stackIn.length-1]);
                    }
                }

                $rootScope.$broadcast('breadcrumbChangeSuccess',stack);
            }

            /**
             * <p>This function will set the currently viewed page to be the one described in the 'item'.</p>
             */

            function navigateTo(item) {
                if(!item) {
                    $location.path('/').search({});
                }
                else {
                    $location.path(item.path);
                }
            }

            /**
             * <p>The creation of a view pkg breadcrumb is a bit complex.  This function will take care of it
             * based on the details provided.</p>
             */

            function createViewPkgBreadcrumbItem(pkgName,versionCoordinates,architectureCode) {
                if(!pkgName||!pkgName.length) {
                    throw 'the package name must be supplied';
                }

                if(!versionCoordinates||!versionCoordinates.major) {
                    throw 'version coordinates must be supplied';
                }

                if(!architectureCode||!architectureCode.length) {
                    throw 'the architectureCode must be supplied to create a view pkg';
                }

                var parts = [
                    'pkg',
                    pkgName,
                    versionCoordinates.major,
                    versionCoordinates.minor ? versionCoordinates.minor : '',
                    versionCoordinates.micro ? versionCoordinates.micro : '',
                    versionCoordinates.preRelease ? versionCoordinates.preRelease : '',
                    versionCoordinates.revision ? versionCoordinates.revision : '',
                    architectureCode
                ];

                return {
                    titleKey : 'breadcrumb.viewPkg.title',
                    titleParameters : [ pkgName ],
                    path : '/' + parts.join('/')
                };
            }

            return {

                // -----------------
                // MANIPULATE THE BREADCRUMB STACK

                stack : function() {
                    return stack;
                },

                reset : function(stackIn) {
                    reset(stackIn);
                },

                pop : function() {
                    return pop();
                },

                popAndNavigate: function() {
                    pop();
                    navigateTo(peek());
                },

                peek : function() {
                    return peek();
                },

                popTo: function(item) {
                    return popTo(item);
                },

                popAndNavigateTo: function(item) {
                    popTo(item);
                   navigateTo(item);
                },

                mergeCompleteStack : function(stackIn) {
                    mergeCompleteStack(stackIn);
                },

                // -----------------
                // CREATE STANDARD BREADCRUMB ITEMS

                createHome : function() {
                    return {
                        titleKey : 'breadcrumb.home.title',
                        path : '/'
                    }
                },

                createEditRepository : function(repository) {
                    return {
                        titleKey : 'breadcrumb.editRepository.title',
                        titleParameters : [ repository.code ],
                        path : '/repository/' + repository.code + '/edit'
                    }
                },

                createAddRepository : function() {
                    return {
                        titleKey : 'breadcrumb.addRepository.title',
                        path : '/repositories/add'
                    }
                },

                createViewRepository : function(repository) {
                    return {
                        titleKey : 'breadcrumb.viewRepository.title',
                        titleParameters : repository.code,
                        path : '/repository/' + repository.code
                    }
                },

                createListRepositories : function() {
                    return {
                        titleKey : 'breadcrumb.listRepositories.title',
                        path : '/repositories'
                    };
                },

                createRuntimeInformation : function() {
                    return {
                        titleKey : 'breadcrumb.runtimeInformation.title',
                        path : '/runtimeinformation'
                    };
                },

                createAbout : function() {
                    return {
                        titleKey : 'breadcrumb.about.title',
                        path : '/about'
                    };
                },

                /**
                 * <p>This function will return a breadcrumb for viewing a specific package.  It expects
                 * a data structure similar to the API return data from "GetPkgResult" where only the
                 * first version is considered.</p>
                 */

                createViewPkgWithSpecificVersionFromPkg : function(pkg) {
                    if(!pkg || !pkg.versions.length) {
                        throw 'a package and a package version are required to form a breadcrumb';
                    }

                    return createViewPkgBreadcrumbItem(
                        pkg.name,
                        pkg.versions[0],
                        pkg.versions[0].architectureCode);
                },

                createViewPkgWithSpecificVersionFromRouteParams : function(routeParams) {

                    if(!routeParams||!routeParams.major) {
                        throw 'route params are expected';
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
                    return {
                        titleKey : 'breadcrumb.viewUser.title',
                        titleParameters : [ user.nickname ],
                        path : '/user/' + user.nickname
                    };
                },

                createChangePassword : function(user) {
                    return {
                        titleKey : 'breadcrumb.changePassword.title',
                        path : '/user/' + user.nickname + '/changepassword'
                    };
                }

            };

        }
    ]
);