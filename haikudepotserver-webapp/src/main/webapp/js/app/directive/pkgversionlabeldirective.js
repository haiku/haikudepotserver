/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span that describes a package, it's version and it's architecture.  The expected
 * input data structure contains the following data;</p>
 *
 * <ul>
 *     <li>major</li>
 *     <li>minor</li>
 *     <li>micro</li>
 *     <li>preRelease</li>
 *     <li>revision</li>
 *     <li>pkg.name</li>
 * </ul>
 */

angular.module('haikudepotserver').directive(
    'pkgVersionLabel',
    ['breadcrumbs','$location',
        function(breadcrumbs,$location) {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    var pkgVersionExpression = attributes['pkgVersion'];
                    var pkgVersionHyperlinkPath = undefined;

                    if(!pkgVersionExpression || !pkgVersionExpression.length) {
                        throw 'expected expression for "pkgVersion"';
                    }

                    var containerEl = angular.element('<span></span>');
                    var hyperlinkEl = angular.element('<a href=""></a>');
                    element.replaceWith(containerEl);
                    containerEl.append(hyperlinkEl);

                    hyperlinkEl.on('click', function(el) {
                        if(pkgVersionHyperlinkPath) {
                            $scope.$apply(function() {
                                $location.path(pkgVersionHyperlinkPath).search({});
                            });
                        }
                        el.preventDefault();
                    });

                    function refresh(pkgVersion) {

                        function pkgVersionElementsToString() {
                            var parts = [ pkgVersion.major ];

                            if (pkgVersion.minor) {
                                parts.push(pkgVersion.minor);
                            }

                            if (pkgVersion.micro) {
                                parts.push(pkgVersion.micro);
                            }

                            if (pkgVersion.preRelease) {
                                parts.push(pkgVersion.preRelease);
                            }

                            if (pkgVersion.revision) {
                                parts.push('' + pkgVersion.revision);
                            }

                            return parts.join('.');
                        }

                        if (!pkgVersion) {
                            hyperlinkEl.text('');
                            pkgVersionHyperlinkPath = undefined;
                        }
                        else {

                            pkgVersionHyperlinkPath = breadcrumbs.createViewPkgWithSpecificVersionFromPkgVersion(pkgVersion).path;

                            hyperlinkEl.text(
                                    pkgVersion.pkg.name +
                                    ' - ' +
                                    pkgVersionElementsToString(pkgVersion) +
                                    ' - ' +
                                    pkgVersion.architectureCode
                            );

                        }
                    }

                    $scope.$watch(pkgVersionExpression, function(newValue) {
                        refresh(newValue);
                    });

                    refresh($scope.$eval(pkgVersionExpression));

                }
            };
        }
    ]
);