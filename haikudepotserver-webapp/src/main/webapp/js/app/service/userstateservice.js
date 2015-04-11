/*
 * Copyright 2013-2015, Andrew Lindesay
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
        '$log','$q','$rootScope','$timeout','$window','$location',
        'jsonRpc','pkgScreenshot','errorHandling',
        'constants','referenceData','jobs','jwt',
        function(
            $log,$q,$rootScope,$timeout,$window,$location,
            jsonRpc,pkgScreenshot,errorHandling,
            constants,referenceData,jobs,jwt) {

            var SIZE_CHECKED_PERMISSION_CACHE = 25;
            var SAMPLESIZE_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS = 10;
            var MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS = 60 * 1000; // 1 min.

            var HDS_TOKEN_KEY = 'hds.userstate.token';
            var HDS_NATURALLANGUAGECODE_KEY = 'hds.userstate.naturallanguagecode';

            var timestampsOfLastTokenRenewals = [];
            var tokenRenewalTimeoutPromise = undefined;

            // this storage is only used if the local storage is not available.

            var userStateData = {
                checkedPermissionCache : [],
                checkQueue : [],
                naturalLanguageCode : 'en',
                token : undefined,
                currentTokenUserNickname : undefined
            };

            // ------------------------------
            // STATE ACCESSOR
            // This is a series of getter-setters that either access local storage (shared between
            // windows) or a data structure within the scope of this service.

            function naturalLanguageCode(value) {

                if(undefined !== value) {

                    if(!value || !value.match(/^[a-z]{2}$/)) {
                        throw Error('the value \''+value+'\' is not a valid natural language code');
                    }

                    if(naturalLanguageCode() != value) {

                        var oldNaturalLanguageCode = naturalLanguageCode();

                        if(null==value) {

                            if(window.localStorage) {
                                window.localStorage.removeItem(HDS_USER_KEY);
                            }

                            userStateData.naturalLanguageCode = undefined;
                        }
                        else {

                            if(window.localStorage) {
                                window.localStorage.setItem(HDS_NATURALLANGUAGECODE_KEY, value);
                            }

                            userStateData.naturalLanguageCode = value;

                        }

                        $rootScope.$broadcast(
                            'naturalLanguageChange',
                            value,
                            oldNaturalLanguageCode
                        );
                    }
                }

                if(window.localStorage) {
                    return window.localStorage.getItem(HDS_NATURALLANGUAGECODE_KEY);
                }

                return userStateData.naturalLanguageCode;
            }

            function user() {
                var tokenValue = token();

                if(tokenValue) {
                    return { nickname : jwt.tokenNickname(tokenValue) };
                }

                return undefined;
            }

            function token(value) {

                if(undefined !== value) {

                    if (null == value) {

                        if (userStateData.currentTokenUserNickname) {

                            if (window.localStorage) {
                                window.localStorage.removeItem(HDS_TOKEN_KEY);
                            }

                            $rootScope.$broadcast('userChangeStart', null);

                            userStateData.token = undefined;

                            // remove the Authorization header for HTTP transport
                            jsonRpc.setHeader('Authorization');
                            pkgScreenshot.setHeader('Authorization');
                            jobs.setHeader('Authorization');

                            cancelTokenRenewalTimeout();
                            userStateData.currentTokenUserNickname = undefined;
                            resetAuthorization();

                            $rootScope.$broadcast('userChangeSuccess', null);
                        }
                    }
                    else {
                        if (jwt.millisUntilExpirationForToken(value) <= 0) {
                            throw Error('at attempt has been made to set a token that has expired already');
                        }

                        var newUser = { nickname : jwt.tokenNickname(value) };

                        if(!userStateData.currentTokenUserNickname || userStateData.currentTokenUserNickname != newUser.nickname) {
                            $rootScope.$broadcast('userChangeStart', newUser);
                        }

                        if (window.localStorage) {
                            window.localStorage.setItem(HDS_TOKEN_KEY, value);
                        }
                        else {
                            userStateData.token = value;
                        }

                        var authenticationContent = 'Bearer ' + value;

                        jsonRpc.setHeader('Authorization', authenticationContent);
                        pkgScreenshot.setHeader('Authorization', authenticationContent);
                        jobs.setHeader('Authorization', authenticationContent);

                        configureTokenRenewal();

                        if(!userStateData.currentTokenUserNickname || userStateData.currentTokenUserNickname != newUser.nickname) {
                            $rootScope.$broadcast('userChangeSuccess', newUser);
                            resetAuthorization();
                        }

                        userStateData.currentTokenUserNickname = newUser.nickname;
                    }
                }

                if(window.localStorage) {
                    return window.localStorage.getItem(HDS_TOKEN_KEY);
                }

                return userStateData.token;
            }

            // ------------------------------
            // FOREGROUND / BACKGROUND JWT UPDATING
            // The JWT will eventually expire.  This set of functions is for avoiding that in the case
            // by always fetching a new one just before the old one expires.

            function cancelTokenRenewalTimeout() {
                if(tokenRenewalTimeoutPromise) {
                    $timeout.cancel(tokenRenewalTimeoutPromise);
                    tokenRenewalTimeoutPromise = undefined;
                }
            }

            function configureTokenRenewal() {

                cancelTokenRenewalTimeout();

                if(token()) {

                    var millisUntilExpiration = jwt.millisUntilExpirationForToken(token());

                    if(millisUntilExpiration > 5000) {

                        // the logic here is that the re-establishment of the token should happen before it will
                        // expire.  If there are many windows open, it is undesirable that they all go and
                        // re-establish their tokens at once.  To avoid this, some random aspect is introduced
                        // to reduce the chance of this happening.

                        var millisUntilRenewal = ((millisUntilExpiration - 5000) * 0.75) + (3000 * Math.random());

                        $log.info('will schedule token renewal in ~' + Math.ceil(millisUntilRenewal / 1000) + "s");

                        tokenRenewalTimeoutPromise = $timeout(function () {
                                if (jwt.millisUntilExpirationForToken(token()) < 0) {
                                    $log.info('am going to renew token, but it has already expired');
                                    errorHandling.navigateToError(jsonRpc.errorCodes.AUTHORIZATIONFAILURE); // simulates this happening
                                }
                                else {

                                    // -------------
                                    // START : EXCESSIVE TOKEN RENEWAL UPDATE CHECK
                                    // as a safety measure (just in case) check to make sure that the renewal of the
                                    // token is not happening too frequently.  This theoretically won't happen.

                                    var nowMs = new Date().getTime();

                                    if(timestampsOfLastTokenRenewals.length = SAMPLESIZE_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS) {
                                        var firstMs = timestampsOfLastTokenRenewals.shift();

                                        if(nowMs - firstMs < MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS) {
                                            throw Error('10 or more renewals of tokens in < ' + MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS + 'ms -- something wrong; failing');
                                        }
                                    }

                                    timestampsOfLastTokenRenewals.push(nowMs);

                                    // END : EXCESSIVE TOKEN RENEWAL UPDATE CHECK
                                    // -------------

                                    jsonRpc.call(
                                        constants.ENDPOINT_API_V1_USER,
                                        'renewToken',
                                        [ { token: token() } ]
                                    ).then(
                                        function (renewTokenResponse) {
                                            if (renewTokenResponse.token) {
                                                token(renewTokenResponse.token);
                                                $log.info('did renew the authentication token');
                                                configureTokenRenewal();
                                            }
                                            else {
                                                $log.info('was not able to renew authentication token');
                                                errorHandling.navigateToError(jsonRpc.errorCodes.AUTHORIZATIONFAILURE); // simulates this happening
                                            }
                                        },
                                        function (err) {
                                            $log.info('failure to renew the authentication token');
                                            errorHandling.handleJsonRpcError(err);
                                        }
                                    );
                                }
                            },
                            millisUntilRenewal
                        );

                    }
                    else {
                        $log.warn('will not schedule token renewal as the token has ether already expired or is about to');
                    }
                }
            }

            // ------------------------------
            // USER HANDLING

            /**
             * <p>This function will look to see if there is a token in the local storage.  If there is then
             * it can load that into the state.</p>
             */

            function initToken() {
                var t = token();

                if (t) {
                    if (jwt.millisUntilExpirationForToken(t) > 0) {
                        token(t);
                    }
                    else {
                        token(null);
                    }
                }
            }

            initToken();

            // this event fires when another window has made a change to the token.

            window.addEventListener('storage', function(e) {
                if(e.key == HDS_TOKEN_KEY) {
                    $log.info('did receive token storage change from another window');
                    token(e.newValue);
                }
            });

            // ------------------------------
            // AUTHORIZATION

            function validateTargetAndPermissions(targetAndPermissions) {
                _.each(targetAndPermissions, function(targetAndPermission) {
                    if(undefined === targetAndPermission.targetType || !_.contains(['PKG','USER','REPOSITORY','USERRATING',null],targetAndPermission.targetType)) {
                        throw Error('illegal argument; bad targetType supplied');
                    }

                    if(undefined === targetAndPermission.targetIdentifier) {
                        throw Error('illegal argument; bad targetIdentifier supplied');
                    }

                    if(!targetAndPermission.permissionCode) {
                        throw Error('illegal argument; bad permission code');
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
                                throw Error('illegal state; top-most request has no uncached target and permissions');
                            }

                            jsonRpc.call(
                                constants.ENDPOINT_API_V1_AUTHORIZATION,
                                'checkAuthorization',
                                [{ targetAndPermissions : uncachedTargetAndPermissions }]
                            ).then(
                                function(data) {

                                    // blend the new material into the cache.

                                    userStateData.checkedPermissionCache = data.targetAndPermissions.concat(userStateData.checkedPermissionCache);

                                    // we should now be in a position to resolve the permission from the cache.

                                    result = tryDeriveFromCache(request.targetAndPermissions);

                                    if(!result) {
                                        throw Error('illegal state; was not able to resolve the request from cache after fetching from application server');
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

            /**
             * <p>Does some guess work and creates a list of preferred natural languages.  The most preferred
             * language is at offset 0, the least preferred language is at the end of the array.</p>
             */

            function guessedNaturalLanguageCodes() {
                var result = [constants.NATURALLANGUAGECODE_ENGLISH]; // default to English

                if(window && window.navigator && window.navigator.language) {
                    var languageMatch = window.navigator.language.match(/^([a-z]{2})($|-.*$)/);

                    if (languageMatch) {
                        result.unshift(languageMatch[1]);
                    }
                }

                var queryParam = $location.search()[constants.KEY_NATURALLANGUAGECODE];

                if(queryParam && queryParam.length) {
                    var queryParamMatch = queryParam.match(/^[a-z]{2}$/);

                    if(queryParamMatch) {
                        result.unshift(queryParam);
                    }
                }

                return result;
            }

            /**
             * <p>This function will take a guess at the default natural language by looking at those languages that
             * are to be found in the browser's own list of languages.</p>
             */

            function initNaturalLanguageCode() {

                // if there is an existing natural language code on the local storage then use that
                // and there is actually no need to initialize the natural language.

                if(!window.localStorage || !window.localStorage.getItem(HDS_NATURALLANGUAGECODE_KEY)) {
                    naturalLanguageCode(constants.NATURALLANGUAGECODE_ENGLISH);
                    referenceData.naturalLanguages().then(
                        function (naturalLanguages) {
                            naturalLanguageCode(_.find(
                                guessedNaturalLanguageCodes(),
                                function (naturalLanguageCode) {
                                    return !!_.findWhere(naturalLanguages, {code: naturalLanguageCode});
                                }
                            ));
                        }
                    );
                }
            }

            initNaturalLanguageCode();

            // ------------------------------
            // PUBLIC INTERFACE

            return {

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
                 * <p>This will obtain the current user.</p>
                 */

                user : function() {
                    return user();
                },

                /**
                 * <p>This function will configure the current user.  The data structure is supposed to hold the
                 * user's "nickname" as well as a "token" value.  It will return the current user.</p>
                 */

                token : function(tokenValue) {
                    return token(tokenValue);
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
                 * <p>Returned is a promise which resolves to a true if all of the queries are true.</p>
                 */

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

        }
    ]
);