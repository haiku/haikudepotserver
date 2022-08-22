/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This filter will present a timestamp that is expressed as milliseconds since the epoc relative to UTC into a
 * local timestamp of the browser.</p>
 */

angular.module('haikudepotserver').filter('timestamp', function() {
        return function(input) {
            return moment.utc(input).local().format('YYYY-MM-DD HH:mm:ss');
        }
    }
);