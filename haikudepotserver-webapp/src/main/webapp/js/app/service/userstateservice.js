/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is here to maintain the current user's state.  When the user logs in for example, this is stored
 * here.  This service may take other actions such as configuring headers in the jsonRpc service when the user logs-in
 * or logs-out.</p>
 *
 * <p>This service also manages authorization information.  There is the possibility to "check" on a permission.</p>
 */

angular.module('haikudepotserver').factory('userState',
    [
        '$log','$q','$rootScope','jsonRpc',
        'pkgIcon','pkgScreenshot','errorHandling','constants','referenceData',
        function(
            $log,$q,$rootScope,jsonRpc,
            pkgIcon,pkgScreenshot,errorHandling,constants,referenceData) {

            const SIZE_CHECKED_PERMISSION_CACHE = 25;

            var userStateData = {};
            userStateData.naturalLanguageCode = 'en';
            userStateData.user = undefined;

            // ------------------------------
            // USER

            function setUser(value) {

                $rootScope.$broadcast('userChangeStart',value);

                resetAuthorization();

                if(null==value) {
                    userStateData.user = undefined;

                    // remove the Authorization header for HTTP transport
                    jsonRpc.setHeader('Authorization');
                    pkgScreenshot.setHeader('Authorization');
                }
                else {

                    if(!value.nickname) {
                        throw 'the nickname is required when setting a user';
                    }

                    if(!value.passwordClear) {
                        throw 'the password clear is required when setting a user';
                    }

                    var basic = 'Basic '+window.btoa(''+value.nickname+':'+value.passwordClear);

                    jsonRpc.setHeader('Authorization',basic);
                    pkgScreenshot.setHeader('Authorization',basic);

                    userStateData.user = {
                        nickname : value.nickname,
                        passwordClear : value.passwordClear,
                        naturalLanguageCode : naturalLanguageCode() // maintain the current setting in the meantime
                    }

                    // now pull back to the server to incorporate other data about the user.  This may not be
                    // required for a desktop application, but is necessary here in order to obtain the
                    // natural language of the user.  Note that owing to the async communication going on here,
                    // the change of the natural language may happen some time after the user has actually changed.

                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_USER,
                        "getUser",
                        [ { nickname : value.nickname }]).then(
                        function(data) {
                            var u = userStateData.user;

                            if(u && u.naturalLanguageCode && u.naturalLanguageCode != data.naturalLanguageCode) {
                                userStateData.user.naturalLanguageCode = data.naturalLanguageCode;
                                userStateData.naturalLanguageCode = data.naturalLanguageCode;
                                $rootScope.$broadcast('naturalLanguageChange',value);
                            }
                        },
                        function(e) {
                            errorHandling.handleJsonRpcError(e);
                        }
                    )

                    $log.info('have set user; '+userStateData.user.nickname);
                }

                $rootScope.$broadcast('userChangeSuccess',value);
            }

            // ------------------------------
            // AUTHORIZATION

            userStateData.checkedPermissionCache = [];
            userStateData.checkQueue = [];

            function validateTargetAndPermissions(targetAndPermissions) {
                _.each(targetAndPermissions, function(targetAndPermission) {
                    if(undefined === targetAndPermission.targetType || !_.contains(['PKG','USER','REPOSITORY',null],targetAndPermission.targetType)) {
                        throw 'illegal argument; bad targetType supplied';
                    }

                    if(undefined === targetAndPermission.targetIdentifier) {
                        throw 'illegal argument; bad targetIdentifier supplied';
                    }

                    if(!targetAndPermission.permissionCode) {
                        throw 'illegal argument; bad permission code';
                    }
                })
            }

            function resetAuthorization() {
                userStateData.checkedPermissionCache = [];
                userStateData.checkQueue = [];
            }

            function check(targetAndPermissions) {

                // this function will see if it is able to resolve the top-most item in the queue by just looking
                // in the cache.  This function will return the result if there is one from cache; otherwise it
                // will return null.

                function tryDeriveFromCache(targetAndPermissionsToCheckAgainstCache) {

                    if(!targetAndPermissionsToCheckAgainstCache.length) {
                        return null;
                    }

                    if(!userStateData.checkedPermissionCache.length) {
                        return null;
                    }

                    var result = [];

                    for(var i=0;i<targetAndPermissionsToCheckAgainstCache.length;i++) {
                        var cachedTargetAndPermission = _.findWhere(
                            userStateData.checkedPermissionCache,
                            targetAndPermissionsToCheckAgainstCache[i]);

                        if(!cachedTargetAndPermission) {
                            return null;
                        }
                        else {
                            result.push(cachedTargetAndPermission);
                        }
                    }

                    return result;
                }

                // this function will take the item from the queue and handle it either by looking in the
                // local cache or by talking to the remote application server.

                function handleNextInCheckQueue() {
                    if(userStateData.checkQueue.length) {
                        var request = userStateData.checkQueue[0];
                        var result = tryDeriveFromCache(request.targetAndPermissions);

                        if(null!=result) {
                            request.deferred.resolve(result);
                            userStateData.checkQueue.shift();
                            handleNextInCheckQueue();
                        }
                        else {

                            // we will have to go off to the application server to get the authorizations that we need

                            var uncachedTargetAndPermissions = _.filter(
                                request.targetAndPermissions,
                                function(targetAndPermission) {
                                    return !_.findWhere(userStateData.checkedPermissionCache, targetAndPermission);
                                }
                            );

                            // TODO - might be faster?
                            // if we have not exceeded the cache size, it would make sense to blend in a few more
                            // up-coming requests' data at the same time so that they will also be cached too.  We
                            // might be able to be a bit smarter about this in the future.

                            if(!uncachedTargetAndPermissions.length) {
                                throw 'illegal state; top-most request has no uncached target and permissions';
                            }

                            jsonRpc.call(
                                constants.ENDPOINT_API_V1_MISCELLANEOUS,
                                'checkAuthorization',
                                [{ targetAndPermissions : uncachedTargetAndPermissions }]
                            ).then(
                                function(data) {

                                    // blend the new material into the cache.

                                    userStateData.checkedPermissionCache = data.targetAndPermissions.concat(userStateData.checkedPermissionCache);

                                    // we should now be in a position to resolve the permission from the cache.

                                    result = tryDeriveFromCache(request.targetAndPermissions);

                                    if(!result) {
                                        throw 'illegal state; was not able to resolve the request from cache after fetching from application server';
                                    }

                                    request.deferred.resolve(result);

                                    // now cull the cache so that we're not storing too much material.  This is very
                                    // simplistic; no LRU or anything.

                                    if(userStateData.checkedPermissionCache.length > SIZE_CHECKED_PERMISSION_CACHE) {
                                        userStateData.checkedPermissionCache.splice(
                                            SIZE_CHECKED_PERMISSION_CACHE,
                                                userStateData.checkedPermissionCache.length-SIZE_CHECKED_PERMISSION_CACHE);
                                    }

                                    // drop this request now that it has been dealt with and move onto checking the
                                    // next one.

                                    userStateData.checkQueue.shift();
                                    handleNextInCheckQueue();
                                },
                                function(err) {

                                    // if there is a problem then treat it as 'fatal'

                                    $log.error('a problem has arisen checking the authorization');
                                    errorHandling.handleJsonRpcError(err);
                                    request.deferred.reject();
                                    resetAuthorization();
                                }
                            );

                        }
                    }
                }

                var deferred = $q.defer();

                // try handle this from the cached data.

                var resultFromCache = tryDeriveFromCache(targetAndPermissions);

                if(resultFromCache) {
                    deferred.resolve(resultFromCache);
                }
                else {

                    // push this request to the queue.

                    userStateData.checkQueue.push( {
                        deferred : deferred,
                        targetAndPermissions : targetAndPermissions
                    });

                    if(1==userStateData.checkQueue.length) {
                        handleNextInCheckQueue();
                    }
                }

                return deferred.promise;
            }

            // ------------------------------
            // NATURAL LANGUAGE HANDLING

            function naturalLanguageCode(value) {
                if(undefined !== value) {
                    if(userStateData.user) {
                        throw 'it is not possible to configure the natural language code when there is a user authenticated';
                    }

                    if(!value || !value.match(/^[a-z]{2}$/)) {
                        throw 'the value \''+value+'\' is not a valid natural language code';
                    }

                    if(userStateData.naturalLanguageCode != value) {
                        userStateData.naturalLanguageCode = value;
                        $rootScope.$broadcast('naturalLanguageChange',value);
                    }
                }

                if(userStateData.user) {
                    return userStateData.user.naturalLanguageCode;
                }

                return userStateData.naturalLanguageCode;
            }

            /**
             * <p>This function will take a guess at the default natural language by looking at those languages that
             * are to be found in the browser's own list of languages.</p>
             */

            function initNaturalLanguageCode() {
                if(window && window.navigator && window.navigator.language) {

                    var languageMatch = window.navigator.language.match(/^([a-z]{2})($|-.*$)/);

                    if(languageMatch) {
                        var language = languageMatch[1];

                        referenceData.naturalLanguages().then(
                            function(naturalLanguages) {
                                if(_.findWhere(naturalLanguages, { code : language })) {
                                    naturalLanguageCode(language);
                                }
                            }
                        );
                    }
                }
            }

            initNaturalLanguageCode();

            var UserState = {

                /**
                 * <p>This is the natural language code for the user.  If there is an authenticated user then this
                 * value will be derived from the user details.  If there is no user presently authenticated then
                 * this function will maintain state of the natural language choice itself.</p>
                 * @param value
                 */

                naturalLanguageCode : function(value) {
                    return naturalLanguageCode(value)
                },

                /**
                 * <p>Invoked with no argument, this function will return the user.  If it is supplied with null then
                 * it will set the current user to empty.  If it is supplied with a user value, it will configure the
                 * user.  The user should consist of the 'nickname' and the 'passwordClear'.</p>
                 */

                user : function(value) {
                    if(undefined !== value) {
                        setUser(value);
                    }

                    return userStateData.user;
                },

                /**
                 * <p>This function will check to make sure that the target and permissions supplied are authorized.
                 * The single argument should be an array of objects.  Each object should have the following
                 * elements;</p>
                 *
                 * <ul>
                 *     <li>targetType</li>
                 *     <li>targetIdentifier</li>
                 *     <li>permissionCode</li>
                 * </ul>
                 *
                 * <p>Returned is a promise which resolves to a list of the requested permissions with an additional
                 * property "authorized" which can be either true or false.</p>
                 */

                authorize : function(targetAndPermissions) {
                    validateTargetAndPermissions(targetAndPermissions);
                    return check(targetAndPermissions);
                },

                areAuthorized : function(targetAndPermissions) {
                    validateTargetAndPermissions(targetAndPermissions);

                    var deferred = $q.defer();

                    check(targetAndPermissions).then(
                        function(data) {
                            // now filter through and make sure everything is true.
                            deferred.resolve(!_.find(data, function(item) {
                                return !item.authorized;
                            }));
                        },
                        function() {
                            // error handling should already have been dealt with.
                            deferred.reject();
                        }
                    );

                    return deferred.promise;
                }


            };

            return UserState;

        }
    ]
);