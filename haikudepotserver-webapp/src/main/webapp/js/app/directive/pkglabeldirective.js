/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the package.</p>
 */

angular.module('haikudepotserver').directive('pkgLabel',[
    'standardDirectiveMixins',
    function(standardDirectiveMixins) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var containerEl = angular.element('<span></span>');
                element.replaceWith(containerEl);

                var pkgExpression = attributes['pkg'];

                $scope.$watch(pkgExpression, function (pkg) {
                    containerEl.text(pkg ? pkg.name : '');
                });

            }
        };
    }
]);