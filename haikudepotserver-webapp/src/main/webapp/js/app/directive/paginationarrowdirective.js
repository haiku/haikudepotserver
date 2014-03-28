/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render the "page left" and "page right" arrows in a table of data.</p>
 */

angular.module('haikudepotserver').directive('paginationArrow',function() {
    return {
        restrict: 'E',
        link : function($scope,element,attributes) {

            var direction = attributes['direction'];
            var onPageExpression = attributes['pageClick'];
            var activeExpression = attributes['active'];

            var active = false;
            var svgElement;
            var svg;

            switch(direction) {
                case 'right':
                    svg = '<svg height=\"12\" width=\"12\"><path fill=\"black\" fill-opacity=\"0.5\" d=\"M0 4.5 L0 7.5 L4 7.5 L4 12 L12 6 L4 0 L4 4.5\"/></svg>';
                    break;

                case 'left':
                    svg = '<svg height=\"12\" width=\"12\"><path fill=\"black\" fill-opacity=\"0.5\" d=\"M12 4.5 L12 7.5 L8 7.5 L8 12 L0 6 L8 0 L8 4.5\"/></svg>';
                    break;

                default:
                    throw 'illegal direction on pagination arrow; '+direction;
            }

            // replaces the element supplied with the SVG one.
            element.after(svg);
            svgElement = element.next();
            element.remove();

            svgElement.on('click',function() {
                if(active && onPageExpression) {
                    $scope.$apply(onPageExpression);
                }
            });

            $scope.$watch(activeExpression,function(newValue) {
                if(newValue) {
                    svgElement.children().attr('fill-opacity','1.0');
                }
                else {
                    svgElement.children().attr('fill-opacity','0.5');
                }

                active = newValue;
            });

        }
    };
});