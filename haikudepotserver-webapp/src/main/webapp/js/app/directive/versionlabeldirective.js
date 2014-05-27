/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a version object (with the major, minor etc... values
 * into a block of HTML that is supposed to be a simple and versatile block of text
 * that describes the label.</p>
 */


angular.module('haikudepotserver').directive(
    'versionLabel',
    [
        function() {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    var versionExpression = attributes['version'];

                    if(!versionExpression || !versionExpression.length) {
                        throw 'expected expression for "version"';
                    }

                    var containerEl = angular.element('<span></span>');
                    element.replaceWith(containerEl);

                    function refresh(version) {

                        function versionElementsToString() {
                            var parts = [ version.major ];

                            if (version.minor) {
                                parts.push(version.minor);
                            }

                            if (version.micro) {
                                parts.push(version.micro);
                            }

                            if (version.preRelease) {
                                parts.push(version.preRelease);
                            }

                            if (version.revision) {
                                parts.push('' + version.revision);
                            }

                            return parts.join('.');
                        }

                        containerEl.text(version ? versionElementsToString(version) : '');
                    }

                    $scope.$watch(versionExpression, function(newValue) {
                        refresh(newValue);
                    });

                    refresh($scope.$eval(versionExpression));

                }
            };
        }
    ]
);