/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the package.</p>
 */

angular.module('haikudepotserver').directive('pkgLabel',[
    // no injections
    function() {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var showName = false;
                var pkgExpression = attributes['pkg'];
                var containerEl = angular.element('<span></span>');

                element.replaceWith(containerEl);

                containerEl.on('click', function() {
                    showName = !showName;

                    $scope.$apply(function() {
                        refreshText();
                    });
                });

                function deriveText(pkg) {

                    if(pkg) {
                        if(!showName &&
                            pkg.versions &&
                            1 === pkg.versions.length &&
                            pkg.versions[0].title &&
                            pkg.versions[0].title.length) {
                            return pkg.versions[0].title;
                        }

                        return pkg.name;
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