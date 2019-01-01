/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * This service is a wrapper around the Browser's local storage.  A user may
 * or may not want the Browser's local storage to be used.  This proxy is
 * able to block or allow through local storage access depending on the
 * user's choice.
 */

angular.module('haikudepotserver').factory('localStorageProxy',
    [
        '$log',
        function($log) {

            var HDS_LOCALSTORAGE_ALLOWED_KEY = "hds.localstorage.allowed";

            var buffer = {};

            /**
             * This value can be either undefined or be false.
             */

            var localStorageAllowed = null;

            /**
             * Copy anything out of the local storage into here on startup so
             * that it is possible to operate without the local storage later
             * if it gets disallowed.
             */

            function init() {
                if (window.localStorage) {
                    for (var i = 0; i < window.localStorage.length; i++) {
                        var key = window.localStorage.key(i);
                        buffer[key] = window.localStorage.getItem(key);
                    }
                }
            }

            /**
             * Local storage can only be enabled if the Browser actually
             * supports local storage.
             */

            function canLocalStorageBeAllowed() {
                return !!window.localStorage;
            }

            function isLocalStorageAllowed() {
                var flag;

                if(window.localStorage) {
                    flag = window.localStorage.getItem(HDS_LOCALSTORAGE_ALLOWED_KEY);

                    if (null !== flag && undefined !== flag) {
                        return !!flag;
                    }
                }

                return localStorageAllowed;
            }

            function setLocalStorageAllowed(flag) {
                if (undefined === flag || null === flag) {
                    throw Error('the flag must be supplied as either true|flase');
                }

                if(window.localStorage) {
                    if (flag) {
                        window.localStorage.setItem(HDS_LOCALSTORAGE_ALLOWED_KEY, !!flag);
                        $log.info('local storage allowed');

                        // now transfer any cached values into the local storage.
                        for (var key in buffer) {
                            if (buffer.hasOwnProperty(key)) {
                               window.localStorage.setItem(key, buffer[key]);
                            }
                        }
                    } else {
                        $log.info('local storage disallowed');
                        window.localStorage.clear();
                    }
                }

                localStorageAllowed = flag ? null : flag;
            }

            function setItem(key, value) {
                buffer[key] = value;
                if (isLocalStorageAllowed()) {
                    window.localStorage.setItem(key, value);
                }
            }

            function getItem(key) {
                if (isLocalStorageAllowed()) {
                    return window.localStorage.getItem(key);
                }

                return buffer[key];
            }

            function removeItem(key) {
                if (isLocalStorageAllowed()) {
                    return window.localStorage.removeItem(key);
                }
                delete buffer[key];
            }

            init();

            // external interface

            return {
                'canLocalStorageBeAllowed': canLocalStorageBeAllowed,
                'isLocalStorageAllowed': isLocalStorageAllowed,
                'setLocalStorageAllowed': setLocalStorageAllowed,
                'setItem': setItem,
                'getItem': getItem,
                'removeItem': removeItem
            }
        }
    ]
);
