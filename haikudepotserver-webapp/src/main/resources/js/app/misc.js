/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

// This logic will catch when there is a re-size of the window or a zoom-in/out and will notify parts of the
// system.  They can then adjust the resolution of images that are shown in order that they show sharp at
// different resolutions.

angular.module('haikudepotserver').run(
    [
        '$rootScope', '$window',
        function($rootScope, $window) {

            var fn = _.debounce(function() {
                $rootScope.$broadcast('windowDidResize');
            }, 1000);

            $window.addEventListener('resize', function() {
                fn();
            });
        }
    ]
);