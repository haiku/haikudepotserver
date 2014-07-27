/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render the "page left" and "page right" arrows in a table of data.</p>
 */

angular.module('haikudepotserver').directive('paginationArrow',[
        'constants',
        function(constants) {
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
                            svg = constants.SVG_RIGHT_ARROW;
                            break;

                        case 'left':
                            svg = constants.SVG_LEFT_ARROW;
                            break;

                        default:
                            throw Error('illegal direction on pagination arrow; '+direction);
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
        }
    ]
);