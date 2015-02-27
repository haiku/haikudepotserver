/*
 * Copyright 2013-2015, Andrew Lindesay
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

                var showName = false;
                var pkgExpression = attributes['pkg'];
                var containerEl = angular.element('<span></span>');

                element.replaceWith(containerEl);

                containerEl.on('click', function(event) {
                    showName = !showName;
                    refreshText();
                });

                function deriveText(pkg) {
                    if(pkg) {
                        if(showName) {
                            return pkg.name;
                        }

                        return pkg.title||pkg.name;
                    }

                    return '';
                }

                function refreshText(pkg) {
                    if(!pkg) {
                        pkg = $scope.$eval(pkgExpression);
                    }
                    containerEl.text(deriveText(pkg));
                }

                $scope.$watch(pkgExpression, function (pkg) {
                    refreshText(pkg);
                });

            }
        };
    }
]);