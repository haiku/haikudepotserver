/*
 * Copyright 2013-2026, Andrew Lindesay
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
        'constants', 'referenceData', 'jobs', 'localStorageProxy',
        function(
            $log, $q, $rootScope, $timeout, $window, $location, $cacheFactory,
            remoteProcedureCall, pkgScreenshot, errorHandling,
            constants, referenceData, jobs, localStorageProxy) {

            var CHECKED_PERMISSION_CACHE_SIZE = 1000;

            var HDS_NATURALLANGUAGECODE_KEY = 'hds.userstate.naturallanguagecode';

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
                const nickname = window.HDS_USER_NICKNAME;

                if (nickname) {
                    return {"nickname": nickname};
                }

                return undefined;
            }

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
                            errorHandling.handleRemoteProcedureCallError
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
