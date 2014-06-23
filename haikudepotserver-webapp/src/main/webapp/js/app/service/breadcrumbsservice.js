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

                if(!item.search || !item.search.bcguid) {
                    throw 'a "bcguid" is expected on a breadcrumb item';
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

                    // if the inbound top-of-stack is an existing item that is already on the stack
                    // then we should pop down to that item.

                    var peekStackIn = stackIn[stackIn.length - 1];
                    var matchingStackItem = undefined;

                    if(peekStackIn.search.bcguid) {
                        matchingStackItem = _.find(stack, function(s) { return s.search.bcguid == peekStackIn.search.bcguid; });
                    }

                    if(matchingStackItem) {
                        popTo(matchingStackItem);
                    }
                    else
                    {
                        // if the last item is not the same path, then stick that item at the top
                        // of the stack.

                        if (peek().path != peekStackIn.path) {
                            stack.push(stackIn[stackIn.length - 1]);
                        }
                    }
                }

                // the breadcrumb being merged in may have more search items than are presently in the
                // location - in this case those additional search items need to be merged in.

                if(peek().search && $location.path() == peek().path) {
                    _.each(peek().search, function(value,key) {
                        $location.search(key,value);
                    });
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

                    if(item.search) {
                        $location.search(item.search);
                    }
                }
            }

            /**
             * <p>Many of the URLs related to packages stem from the same base URL.  This function
             * will create that base URL.</p>
             */

            function generateBaseUrlForPkg(pkgName,versionCoordinates,architectureCode) {
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
                    throw 'a package version is required to get the latest';
                }

                var pkgVersion = _.findWhere(pkgVersions, { isLatest : true } );

                if(!pkgVersion) {
                    pkgVersion = pkgVersions[0];
                }

                return pkgVersion;
            }

            function createManipulatePkgBreadcrumbItem(pkgWithVersion0, pathSuffix, titlePortion) {
                if(!pkgWithVersion0 || !pkgWithVersion0.versions || !pkgWithVersion0.versions.length) {
                    throw 'a package version is required to form a breadcrumb';
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
                    throw 'was expecting an item to be supplied to be augmented';
                }

                item.path = $location.path();
                item.search = _.extend(item.search ? item.search : {}, $location.search());

                return item;
            }

            return {

                // -----------------
                // MANIPULATE THE BREADCRUMB STACK

                stack : function() {
                    return stack;
                },

                reset : function(stackIn) {
                    reset(stackIn);
                    return stack;
                },

                resetAndNavigate : function(stackIn) {
                    reset(stackIn);
                    navigateTo(peek());
                    return stack;
                },

                pop : function() {
                    return pop();
                },

                /**
                 * <p>Pop the next item off the stack and navigate to the next one on the stack.  Returns the next
                 * one on the stack.</p>
                 */

                popAndNavigate: function() {
                    pop();

                    if(!stack.length) {
                        throw 'have popped from the stack, but there is now nothing to navigate to';
                    }

                    var top = peek();
                    navigateTo(top);
                    return top;
                },

                peek : function() {
                    return peek();
                },

                /**
                 * <p>This will pop off items from the stack until it reaches this item.  It will then navigate
                 * to the item and will return that item.</p>
                 */

                popToAndNavigate: function(item) {
                    var result = popTo(item);

                    if(!result) {
                        throw 'unable to find the item on the stack';
                    }

                    navigateTo(result);

                    return result;
                },

                /**
                 * <p>This will look to see what is being supplied and compare it with what it has on the stack at
                 * the moment.  It may be possible to just merge the inbound stack in or if the stack is presently
                 * empty then it will just use the inbound stack.</p>
                 */

                mergeCompleteStack : function(stackIn) {
                    mergeCompleteStack(stackIn);
                    return stack;
                },

                pushAndNavigate : function(item) {
                    verifyItem(item);
                    push(item);
                    navigateTo(item);
                    return item;
                },

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
                        throw 'user with nickname is required to make this breadcrumb';
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
                        throw 'a user rating code is expected';
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

                createEditPkgIcon : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editicon', 'editPkgIcon');
                },

                createEditPkgScreenshots : function(pkg) {
                    return createManipulatePkgBreadcrumbItem(pkg, 'editscreenshots', 'editPkgScreenshots');
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

                createEditUserRating : function(userRating) {
                    return applyDefaults({
                        titleKey : 'breadcrumb.editUserRating.title',
                        path : '/userrating/' + userRating.code + '/edit'
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
                        throw 'a package version is required to form a breadcrumb';
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
                        throw 'a package with a package version are required to form a breadcrumb';
                    }

                    var pkgVersion = latestVersion(pkg.versions);

                    return createViewPkgBreadcrumbItem(
                        pkg.name,
                        pkgVersion,
                        pkgVersion.architectureCode);
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
                }

            };

        }
    ]
);