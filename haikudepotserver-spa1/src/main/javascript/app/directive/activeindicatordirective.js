/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small element that indicates if the supplied value is active; for example, a green
 * circle for active and a red circle for inactive.</p>
 */

angular.module('haikudepotserver').directive('activeIndicator',function() {
    return {
        restrict: 'E',
        link : function($scope,element,attributes) {

            var stateExpression = attributes['state'];

            if(!stateExpression || !stateExpression.length) {
                throw Error('a value for the binding \'state\' was expected');
            }

            var svgE = angular.element('<svg width="12px" height="12px"><circle cx="6px" cy="6px" r="5.5px" fill="gray"></circle></svg>');
            element.replaceWith(svgE);

            $scope.$watch(stateExpression, function(newValue) {
                svgE.children().attr('class', [
                        'active-indicator',
                        newValue ? 'active-indicator-true' : 'active-indicator-false'
                    ].join(' ')
                );
            });
        }
    };
});