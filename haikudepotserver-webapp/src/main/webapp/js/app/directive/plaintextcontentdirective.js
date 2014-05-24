/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive takes some plain text that may have newlines etc.. in it and will format it as HTML that
 * has this same sort of appearance.</p>
 */

angular.module('haikudepotserver').directive(
    'plainTextContent',[
        function() {
            return {
                restrict: 'E',
                link: function($scope, element, attributes) {

                    var valueExpression = attributes['value']
                    var containerEl = angular.element('<div></div>');
                    element.replaceWith(containerEl);

                    function encodeLine(line) {
                        // TODO; whitespace at the start?
                        return _.escape(line);
                    }

                    function encodeString(s) {
                        if(!s || !s.length) {
                            return '';
                        }

                        return _.map(
                            s.split(/\r\n|\n/),
                            function(line) {
                                return encodeLine(line);
                            }
                        ).join('<br/>\n');
                    }

                    $scope.$watch(valueExpression, function(newValue) {
                        var newContainerEl = angular.element('<div>'+encodeString(newValue)+'</div>');
                        containerEl.replaceWith(newContainerEl);
                        containerEl = newContainerEl;
                    });
                }
            };
        }
    ]
);