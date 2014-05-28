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
        'standardDirectiveMixins',
        function(standardDirectiveMixins) {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    // apply a mixin for standard directive mixins.
                    angular.extend(this,standardDirectiveMixins);

                    var versionExpression = attributes['version'];

                    if(!versionExpression || !versionExpression.length) {
                        throw 'expected expression for "version"';
                    }

                    var containerEl = angular.element('<span></span>');
                    element.replaceWith(containerEl);

                    function refresh(version) {
                        containerEl.text(version ? pkgVersionElementsToString(version) : '');
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