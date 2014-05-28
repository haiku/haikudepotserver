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
    ['breadcrumbs','$location','standardDirectiveMixins',
        function(breadcrumbs,$location,standardDirectiveMixins) {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    // apply a mixin for standard directive mixins.
                    angular.extend(this,standardDirectiveMixins);

                    var pkgVersionExpression = attributes['pkgVersion'];
                    var shouldLink = attributes['shouldLink'];
                    var pkgVersionHyperlinkPath = undefined;

                    if(!pkgVersionExpression || !pkgVersionExpression.length) {
                        throw 'expected expression for "pkgVersion"';
                    }

                    var containerEl = angular.element('<span></span>');
                    var textTargetEl = containerEl;
                    element.replaceWith(containerEl);

                    if((undefined == shouldLink || 'true' == shouldLink) && !isChildOfForm(containerEl)) {
                        var hyperlinkEl = angular.element('<a href=""></a>');
                        containerEl.append(hyperlinkEl);
                        textTargetEl = hyperlinkEl;

                        hyperlinkEl.on('click', function(el) {
                            if(pkgVersionHyperlinkPath) {
                                $scope.$apply(function() {
                                    $location.path(pkgVersionHyperlinkPath).search({});
                                });
                            }
                            el.preventDefault();
                        });
                    }

                    function refresh(pkgVersion) {

                        if (!pkgVersion) {
                            textTargetEl.text('');
                            pkgVersionHyperlinkPath = undefined;
                        }
                        else {

                            pkgVersionHyperlinkPath = breadcrumbs.createViewPkgWithSpecificVersionFromPkgVersion(pkgVersion).path;

                            textTargetEl.text(
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