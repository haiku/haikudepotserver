/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is here to maintain the current user's state.  When the user logs in for example, this is stored
 * here.  This service may take other actions such as configuring headers in the remoteProcedureCall service when the user logs-in
 * or logs-out.</p>
 *
 * <p>This service also manages authorization information.  There is the possibility to "check" on a permission.</p>
 */

angular.module('haikudepotserver').factory('userState',
    [
        '$log', '$q', '$rootScope', '$timeout', '$window', '$location', '$cacheFactory',
        'remoteProcedureCall', 'pkgScreenshot', 'errorHandling',
        'constants', 'referenceData', 'jobs', 'jwt', 'localStorageProxy',
        function(
            $log, $q, $rootScope, $timeout, $window, $location, $cacheFactory,
            remoteProcedureCall, pkgScreenshot, errorHandling,
            constants, referenceData, jobs, jwt, localStorageProxy) {

            var CHECKED_PERMISSION_CACHE_SIZE = 1000;

            var SAMPLESIZE_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS = 10;
            var MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS = 60 * 1000; // 1 min.

            var HDS_TOKEN_KEY = constants.STORAGE_TOKEN_KEY;
            var HDS_NATURALLANGUAGECODE_KEY = 'hds.userstate.naturallanguagecode';

            var timestampsOfLastTokenRenewals = [];
            var tokenRenewalTimeoutPromise = undefined;

            var authorizationData = undefined;
            resetAuthorization();

            // ------------------------------
            // STATE ACCESSOR
            // This is a series of getter-setters that either access local storage (shared between
            // windows) or a data structure within the scope of this service.

            function naturalLanguageCode(value) {

                if (undefined !== value) {

                    if (!value && !value.match(/^[a-z]{2}$/)) {
                        throw Error('the value \''+value+'\' is not a valid natural language code');
                    }

                    if (naturalLanguageCode() !== value) {

                        var oldNaturalLanguageCode = naturalLanguageCode();

                        if (null === value) {
                            localStorageProxy.removeItem(HDS_NATURALLANGUAGECODE_KEY);
                        }
                        else {
                            localStorageProxy.setItem(HDS_NATURALLANGUAGECODE_KEY, value);
                        }

                        $rootScope.$broadcast(
                            'naturalLanguageChange',
                            value,
                            oldNaturalLanguageCode
                        );
                    }
                }

                return localStorageProxy.getItem(HDS_NATURALLANGUAGECODE_KEY);
            }

            function user() {
                var tokenValue = token();

                if (tokenValue) {
                    var nickname = jwt.tokenNickname(tokenValue);
                    return { nickname : nickname };
                }

                return undefined;
            }

            function token(value) {

                function setAuthorizationHeader(value) {
                    _.each(
                        [remoteProcedureCall, pkgScreenshot, jobs],
                        function(svc) { svc.setHeader('Authorization', value); }
                    );
                }

                if (undefined !== value) {
                    if (null == value) {
                        if (token()) {
                            localStorageProxy.removeItem(HDS_TOKEN_KEY);
                            $rootScope.$broadcast('userChangeStart', null);

                            setAuthorizationHeader();
                            cancelTokenRenewalTimeout();
                            resetAuthorization();

                            $rootScope.$broadcast('userChangeSuccess', null);
                        }
                    }
                    else {

                        if (jwt.millisUntilExpirationForToken(value) <= 0) {
                            throw Error('at attempt has been made to set a token that has expired already');
                        }

                        var oldUser = user();
                        var newUser = { nickname : jwt.tokenNickname(value) };
                        var userChanging = !oldUser || oldUser.nickname !== newUser.nickname;

                        if (userChanging) {
                            $rootScope.$broadcast('userChangeStart', newUser);
                        }

                        localStorageProxy.setItem(HDS_TOKEN_KEY, value);
                        setAuthorizationHeader('Bearer ' + value);
                        configureTokenRenewal();

                        if (userChanging) {
                            resetAuthorization();
                            $rootScope.$broadcast('userChangeSuccess', newUser);
                        }
                    }
                }

                return localStorageProxy.getItem(HDS_TOKEN_KEY);
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
                                    errorHandling.navigateToError(remoteProcedureCall.errorCodes.AUTHORIZATIONFAILURE); // simulates this happening
                                }
                                else {

                                    // -------------
                                    // START : EXCESSIVE TOKEN RENEWAL UPDATE CHECK
                                    // as a safety measure (just in case) check to make sure that the renewal of the
                                    // token is not happening too frequently.  This theoretically won't happen.

                                    var nowMs = new Date().getTime();

                                    if (timestampsOfLastTokenRenewals.length === SAMPLESIZE_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS) {
                                        var firstMs = timestampsOfLastTokenRenewals.shift();

                                        if (nowMs - firstMs < MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS) {
                                            throw Error('10 or more renewals of tokens in < ' + MIN_MILLIS_FOR_TIMESTAMPS_OF_LAST_TOKEN_RENEWALS + 'ms -- something wrong; failing');
                                        }
                                    }

                                    timestampsOfLastTokenRenewals.push(nowMs);

                                    // END : EXCESSIVE TOKEN RENEWAL UPDATE CHECK
                                    // -------------

                                    remoteProcedureCall.call(
                                        constants.ENDPOINT_API_V2_USER,
                                        'renew-token',
                                        { token: token() }
                                    ).then(
                                        function (renewTokenResponse) {
                                            if (renewTokenResponse.token) {
                                                token(renewTokenResponse.token);
                                                $log.info('did renew the authentication token');
                                                configureTokenRenewal();
                                            }
                                            else {
                                                $log.info('was not able to renew authentication token');
                                                errorHandling.navigateToError(remoteProcedureCall.errorCodes.AUTHORIZATIONFAILURE); // simulates this happening
                                            }
                                        },
                                        function (err) {
                                            $log.info('failure to renew the authentication token');
                                            errorHandling.handleRemoteProcedureCallError(err);
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
                    var ms = jwt.millisUntilExpirationForToken(t);

                    if (ms > 0) {
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
                if (e.key === HDS_TOKEN_KEY) {
                    $log.info('did receive token storage change from another window');
                    token(e.newValue);
                }
            });

            // ------------------------------
            // AUTHORIZATION

            function validateTargetAndPermissions(targetAndPermissions) {
                _.each(targetAndPermissions, function(targetAndPermission) {
                    if (undefined === targetAndPermission.targetType || !_.contains(['PKG','USER','REPOSITORY','USERRATING',null],targetAndPermission.targetType)) {
                        throw Error('illegal argument; bad targetType supplied');
                    }

                    if (undefined === targetAndPermission.targetIdentifier) {
                        throw Error('illegal argument; bad targetIdentifier supplied');
                    }

                    if (!targetAndPermission.permissionCode) {
                        throw Error('illegal argument; bad permission code');
                    }
                })
            }

            function resetAuthorization() {
                $log.info('reset authorization');
                authorizationData = {
                    checkedPermissionCache: new LRUCache(CHECKED_PERMISSION_CACHE_SIZE),
                    checkQueue: [],
                    isRootPromise: false
                };
            }

            function checkAuthorizations(targetAndPermissions) {

                if (!targetAndPermissions.length) {
                    throw Error('requested a check on target and permissions, but there were none supplied');
                }

                validateTargetAndPermissions(targetAndPermissions);

                var localAd = authorizationData;

                function findIndexInCheckQueue(targetAndPermission) {
                    return _.findIndex(
                        localAd.checkQueue,
                        function (item) {
                            return matchesTargetAndPermission(item, targetAndPermission);
                        });
                }

                function toCacheKey(targetAndPermission) {
                    function orPlaceholder(v) {
                        return v ? v : 'undefined';
                    }

                    return [
                        targetAndPermission.permissionCode,
                        orPlaceholder(targetAndPermission.targetType),
                        orPlaceholder(targetAndPermission.targetIdentifier)
                    ].join('::');
                }

                function putToCache(targetAndPermission) {
                    localAd.checkedPermissionCache.set(
                        toCacheKey(targetAndPermission), targetAndPermission);
                }

                function getFromCache(targetAndPermission) {
                    return localAd.checkedPermissionCache.get(toCacheKey(targetAndPermission));
                }

                function matchesTargetAndPermission(item, targetAndPermission) {
                    return item.targetType === targetAndPermission.targetType &&
                        item.targetIdentifier === targetAndPermission.targetIdentifier &&
                        item.permissionCode === targetAndPermission.permissionCode;
                }

                // check the cache and then if there's nothing there then create
                // a promise.

                function toPromise(targetAndPermission) {
                    function enqueueTargetAndPermission() {
                        var queueItem = _.extend(targetAndPermission, {'deferred': $q.defer()});
                        localAd.checkQueue.push(queueItem);
                        return queueItem.deferred.promise;
                    }

                    function findExistingInCheckQueue(targetAndPermission) {
                        var checkQueueIndex = findIndexInCheckQueue(targetAndPermission);
                        if (-1 !== checkQueueIndex) {
                            return localAd.checkQueue[checkQueueIndex];
                        }
                    }

                    var existing = getFromCache(targetAndPermission);

                    if (!existing) {
                        existing = findExistingInCheckQueue(targetAndPermission);
                    }

                    return existing ? existing.deferred.promise : enqueueTargetAndPermission();
                }

                function pollQueue() {
                    if (0 !== localAd.checkQueue.length) {

                        function rejectAllQueued() {
                            _.each(localAd.checkQueue, function (item) {
                                item.deferred.reject();
                            });
                        }

                        // this will need to match the inbound data with the data
                        // in the queue.  It then removes the items from the check
                        // queue and resolve the promise there and shifts the entry
                        // into the cache so that further queries will come from
                        // the cache.

                        function handleInboundDataItem(inboundDataItem) {
                            var checkQueueIndex = findIndexInCheckQueue(inboundDataItem);

                            if (-1 !== checkQueueIndex) {
                                var checkQueueItem = localAd.checkQueue.splice(checkQueueIndex, 1)[0];
                                checkQueueItem.authorized = inboundDataItem.authorized;
                                checkQueueItem.deferred.resolve(checkQueueItem);
                                putToCache(checkQueueItem);
                            } else {
                                $log.warn('inbound authorization data [' +
                                    toCacheKey(inboundDataItem) +
                                    '] does not match to items in the queue');
                                errorHandling.navigateToError();
                                rejectAllQueued();
                                resetAuthorization();
                            }
                        }

                        var callTargetAndPermissions = _.map(
                            localAd.checkQueue,
                            function (item) {
                                return {
                                    targetType: item.targetType,
                                    targetIdentifier: item.targetIdentifier,
                                    permissionCode: item.permissionCode
                                };
                            }
                        );

                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_AUTHORIZATION,
                            'check-authorization',
                            { targetAndPermissions: callTargetAndPermissions }
                        ).then(
                            function (data) {
                                _.each(data.targetAndPermissions, function (item) {
                                    handleInboundDataItem(item);
                                });
                                if (0 !== localAd.checkQueue.length) {
                                    pollQueue();
                                }
                            },
                            function (err) {
                                $log.error('a problem has arisen checking the authorization');
                                errorHandling.handleRemoteProcedureCallError(err);
                                rejectAllQueued();
                                resetAuthorization();
                            }
                        );
                    }
                }

                var checkQueueWasEmptyBefore = 0 === localAd.checkQueue.length;
                var result = $q.all(_.map(targetAndPermissions, toPromise));
                var checkQueueIsEmptyAfter = 0 === localAd.checkQueue.length;

                if (checkQueueWasEmptyBefore && !checkQueueIsEmptyAfter) {
                    // so that the processing occurs a little later allowing any other
                    // permissions checks to be captured at once.
                    $timeout(pollQueue, 1);
                }

                return result;
            }

            function checkAllAuthorizationsAreAuthorized(targetAndPermissions) {
                return checkAuthorizations(targetAndPermissions).then(
                    function(data) {
                        // now filter through and make sure everything is true.
                        return !_.find(data, function(item) { return !item.authorized; });
                    }
                );
            }

            // ------------------------------
            // ROOT

            function isRoot() {
                if (!authorizationData.isRootPromise) {
                    var u = user();

                    if (u) {
                        authorizationData.isRootPromise = remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_USER,
                            'get-user',
                            { nickname: u.nickname }
                        ).then(
                            function (result) {
                                return !!result.isRoot;
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    } else {
                        authorizationData.isRootPromise = $q.resolve(false);

                    }
                }

                return authorizationData.isRootPromise;
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

                if (!localStorageProxy.getItem(HDS_NATURALLANGUAGECODE_KEY)) {
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
                 * <p>This is the natural language code for the user.  If there
                 * is an authenticated user then this value will be derived
                 * from the user details.  If there is no user presently
                 * authenticated then this function will maintain state of the
                 * natural language choice itself.</p>
                 * @param value
                 */

                naturalLanguageCode : naturalLanguageCode,

                /**
                 * <p>This will obtain the current user.</p>
                 */

                user : user,

                /**
                 * <p>This function will either set or get the token.  Invoked
                 * with no token value, the function will return the current
                 * token value.  Invoked with a value will set the value and
                 * will return it.</p>
                 */

                token : token,

                /**
                 * <p>This function will check to make sure that the target and
                 * permissions supplied are authorized. The single argument
                 * should be an array of objects.  Each object should have the
                 * following elements;</p>
                 *
                 * <ul>
                 *     <li>targetType</li>
                 *     <li>targetIdentifier</li>
                 *     <li>permissionCode</li>
                 * </ul>
                 *
                 * <p>Returned is a promise which resolves to a true if all of
                 * the queries are true.</p>
                 */

                areAuthorized : checkAllAuthorizationsAreAuthorized,

                /**
                 * <p>Returns a boolean that resolves to true if the user is a
                 * root user.</p>
                 */

                isRoot: isRoot

            };

        }
    ]
);
