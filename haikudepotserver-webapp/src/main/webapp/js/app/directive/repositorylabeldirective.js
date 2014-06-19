/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the repository.</p>
 */

angular.module('haikudepotserver').directive('repositoryLabel',[
    '$location',
    'standardDirectiveMixins','userState','breadcrumbs',
    function(
        $location,
        standardDirectiveMixins,userState,breadcrumbs) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this, standardDirectiveMixins);

                var containerEl = angular.element('<span></span>');
                var breadcrumbItem = undefined;
                element.replaceWith(containerEl);

                var repositoryExpression = attributes['repository'];
                var shouldLink = attributes['shouldLink'];

                if (!repositoryExpression || !repositoryExpression.length) {
                    throw 'the repository expression should be defined';
                }

                $scope.$watch(repositoryExpression, function (repository) {

                    function setupForNoHyperlink() {
                        if('span' != containerEl[0].tagName.toLowerCase()) {
                            var el = angular.element('<span></span>');
                            containerEl.replaceWith(el);
                            containerEl = el;
                        }

                        breadcrumbItem = undefined;
                        containerEl.text(repository ? repository.code : '');
                    }

                    // we may want an anchor or we may want simple text.

                    if ((undefined == shouldLink || 'true' == shouldLink) && repository && !isChildOfForm(containerEl)) {

                        userState.areAuthorized([{
                            targetType:'REPOSITORY',
                            targetIdentifier:repository.code,
                            permissionCode:'REPOSITORY_VIEW'
                        }]).then(function(flag) {

                            if(flag) {
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

                                    containerEl.text(repository ? repository.code : '');
                                }

                                breadcrumbItem = breadcrumbs.createViewRepository(repository);
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