/*
 * Copyright 2014-2016, Andrew Lindesay
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
        'standardDirectiveFunctions',
        function(standardDirectiveFunctions) {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    var versionExpression = attributes['version'];

                    if(!versionExpression || !versionExpression.length) {
                        throw Error('expected expression for "version"');
                    }

                    var containerEl = angular.element('<span></span>');
                    element.replaceWith(containerEl);

                    function refresh(version) {
                        containerEl.text(version ? standardDirectiveFunctions.pkgVersionElementsToString(version) : '');
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