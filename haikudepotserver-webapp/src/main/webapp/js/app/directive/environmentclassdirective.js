/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will stick some class into the "HTML" tag so that CSS rules can know that the browser
 * does not support certain features.</p>
 */

angular.module('haikudepotserver').directive(
    'environmentClass',[
        // no injections
        function() {
            return {
                restrict: 'A',
                replace: true,
                link: function(scope, elem) {
                    if (!Modernizr.svg) {
                        elem.addClass('nosvg');
                    }
                }
            };
        }
    ]
);