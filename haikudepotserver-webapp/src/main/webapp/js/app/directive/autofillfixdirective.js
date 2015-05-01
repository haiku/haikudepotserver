/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive fixes a known problem with AngularJS (and maybe other javascript frameworks) where an autofilled
 * field is not raising the events required to get the scope to get updated.  This, in turn, means that the model is
 * not updated.</p>
 *
 * <p>This is not very nice, but does seem to be the only way to work around this.</p>
 */

angular.module('haikudepotserver').directive(
    'autofillFix',[
        '$timeout','$log',
        function($timeout,$log) {
            return {
                restrict: 'A',
                replace: true,
                link: function(scope, elem) {
                    $timeout(function() {
                        if(!!window.CustomEvent) {
                            elem[0].dispatchEvent(new CustomEvent('change'));
                        }
                        else {

                            // This is an older, discouraged way to do this, but it may
                            // work with older browsers.

                            var event = document.createEvent('Event');
                            if(event)
                            event.initEvent('change', true, true);
                            elem[0].dispatchEvent(event);
                        }
                    },100);
                }
            };
        }
    ]
);