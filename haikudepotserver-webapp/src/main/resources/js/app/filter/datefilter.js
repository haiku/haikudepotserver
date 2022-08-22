/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This filter will present a timestamp that is expressed as milliseconds since the epoc relative to UTC into a
 * local timestamp of the browser, but as a date only string with no time component.</p>
 */

angular.module('haikudepotserver').filter('date', function() {
        return function(input) {
            return moment.utc(input).local().format('YYYY-MM-DD');
        }
    }
);