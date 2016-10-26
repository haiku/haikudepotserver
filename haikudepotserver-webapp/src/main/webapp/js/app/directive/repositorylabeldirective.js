/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the repository.</p>
 */

angular.module('haikudepotserver').directive('repositoryLabel',[
    '$location',
    'standardDirectiveFunctions','userState','breadcrumbs','breadcrumbFactory',
    function(
        $location,
        standardDirectiveFunctions,userState,breadcrumbs,breadcrumbFactory) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var containerEl = angular.element('<span></span>');
                var breadcrumbItem = undefined;
                element.replaceWith(containerEl);

                var repositoryExpression = attributes['repository'];
                var shouldLink = attributes['shouldLink'];

                if (!repositoryExpression || !repositoryExpression.length) {
                    throw Error('the repository expression should be defined');
                }

                $scope.$watch(repositoryExpression, function(repository) {

                    function deriveTitle(repository) {
                        if(repository) {
                            if (repository.name) {
                                return repository.name;
                            }

                            if (repository.code) {
                                return repository.code;
                            }
                        }

                        return '?';
                    }

                    function setupForNoHyperlink() {
                        if('span' != containerEl[0].tagName.toLowerCase()) {
                            var el = angular.element('<span></span>');
                            containerEl.replaceWith(el);
                            containerEl = el;
                        }

                        breadcrumbItem = undefined;
                        containerEl.text(deriveTitle(repository));
                    }

                    // we may want an anchor or we may want simple text.

                    if ((undefined == shouldLink || 'true' == shouldLink) &&
                        repository &&
                        !standardDirectiveFunctions.isChildOfForm(containerEl)) {

                        userState.areAuthorized([{
                            targetType:'REPOSITORY',
                            targetIdentifier:repository.code,
                            permissionCode:'REPOSITORY_VIEW'
                        }]).then(function(flag) {

                            if(flag) {
                                breadcrumbItem = breadcrumbFactory.createViewRepository(repository);

                                if ('a' != containerEl[0].tagName.toLowerCase()) {
                                    var anchorEl = angular.element('<a href=""></a>');
                                    containerEl.replaceWith(anchorEl);
                                    containerEl = anchorEl;

                                    containerEl.on('click', function (el) {
                                        if (breadcrumbItem) {
                                            $scope.$apply(function () {
                                                breadcrumbs.pushAndNavigate(breadcrumbItem);
                                            });
                                        }
                                        el.preventDefault();
                                    });

                                    containerEl.attr(
                                        'href',
                                        breadcrumbFactory.toFullPath(breadcrumbItem)
                                    );

                                    containerEl.text(deriveTitle(repository));
                                }
                            }
                            else {
                                setupForNoHyperlink();
                            }
                        });

                    }
                    else {
                        setupForNoHyperlink();
                    }

                });

            }
        }
    }
]);