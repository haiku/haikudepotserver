/*
 * Copyright 2014-2016, Andrew Lindesay
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
    ['breadcrumbs','breadcrumbFactory','standardDirectiveFunctions',
        function(breadcrumbs,breadcrumbFactory,standardDirectiveFunctions) {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    var pkgVersionExpression = attributes['pkgVersion'];
                    var shouldLink = attributes['shouldLink'];
                    var pkgVersionBreadcrumbItem = undefined;

                    if(!pkgVersionExpression || !pkgVersionExpression.length) {
                        throw Error('expected expression for "pkgVersion"');
                    }

                    var containerEl = angular.element('<span></span>');
                    var textTargetEl = containerEl;
                    element.replaceWith(containerEl);

                    if((undefined == shouldLink || 'true' == shouldLink) &&
                        !standardDirectiveFunctions.isChildOfForm(containerEl)) {

                        var hyperlinkEl = angular.element('<a href=""></a>');
                        containerEl.append(hyperlinkEl);
                        textTargetEl = hyperlinkEl;

                        hyperlinkEl.on('click', function(event) {
                            if(pkgVersionBreadcrumbItem) {
                                $scope.$apply(function() {
                                    breadcrumbs.pushAndNavigate(pkgVersionBreadcrumbItem);
                                });
                            }
                            event.preventDefault();
                        });

                    }

                    function refresh(pkgVersion) {

                        if (!pkgVersion) {
                            textTargetEl.text('');
                            pkgVersionBreadcrumbItem = undefined;
                        }
                        else {

                            pkgVersionBreadcrumbItem = breadcrumbFactory.createViewPkgWithSpecificVersionFromPkgVersion(pkgVersion);

                            var parts = [
                                pkgVersion.pkg.name,
                                ' - ',
                                standardDirectiveFunctions.pkgVersionElementsToString(pkgVersion),
                                ' - ',
                                pkgVersion.architectureCode
                            ];

                            if(pkgVersion.repositoryCode) {
                                parts.push(' @ ');
                                parts.push(pkgVersion.repositoryCode);
                            }

                            textTargetEl.text(parts.join(''));

                            if('A' == textTargetEl[0].tagName.toUpperCase()) {
                                textTargetEl.attr(
                                    'href',
                                    breadcrumbFactory.toFullPath(pkgVersionBreadcrumbItem)
                                );
                            }

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