/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This filter will take a (long) guid and will show only the start of it with an ellipses.</p>
 */

angular.module('haikudepotserver').filter('croppedGuid', function() {
        return function(input) {
            if(input && input.length) {
                if(input.length > 4) {
                    return input.substr(0,4) + '...';
                }
            }

            return input;
        }
    }
);