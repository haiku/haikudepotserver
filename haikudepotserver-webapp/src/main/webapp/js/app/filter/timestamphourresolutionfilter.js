/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This filter will present a timestamp that is expressed as milliseconds
 * since the epoc relative to UTC into a local timestamp of the browser.  It
 * will however only display the hour component and will not show the minutes
 * or seconds.</p>
 */

angular.module('haikudepotserver').filter('timestampHourResolution', function() {
        return function(input) {
            return moment.utc(input).local().format('YYYY-MM-DD HH:' + '--:--');
        }
    }
);