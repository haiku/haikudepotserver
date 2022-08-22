/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive can be added to an input field.  It will ensure that once the page is 'built' that the
 * associated 'INPUT' field gets the focus.</p>
 */

angular.module('haikudepotserver').directive(
    'initialFocus',[
        '$timeout',
        function($timeout) {
            return {
                restrict: 'A',
                replace: true,
                link: function(scope, elem) {
                    $timeout(function() {
                        elem[0].focus();
                    },0);
                }
            };
        }
    ]
);