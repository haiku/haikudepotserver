/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will allow an action to be fired when the return key is pressed inside an input field.</p>
 */

angular.module('haikudepotserver').directive(
    'returnKeyPress',[
        // no injections
        function() {
            return {
                restrict: 'A',
                link: function(scope, elem, attributes) {

                    elem.on('keypress', function(event) {

                        if(0x0d == event.keyCode) {
                            event.preventDefault();

                            var expression = attributes['returnKeyPress'];

                            if (expression && expression.length) {
                                scope.$apply(expression);
                            }
                        }

                    });

                }
            };
        }
    ]
);