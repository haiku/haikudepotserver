/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the repository source.
 * The repository source is expected to have a repository object attached to it or to
 * have a repositoryCode associated with it.
 * </p>
 */

angular.module('haikudepotserver').directive('repositorySourceLabel',[
    '$location',
    'standardDirectiveMixins','userState','breadcrumbs','breadcrumbFactory',
    function(
        $location,
        standardDirectiveMixins,userState,breadcrumbs,breadcrumbFactory) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this, standardDirectiveMixins);

                var containerEl = angular.element('<span></span>');
                var breadcrumbItem = undefined;
                element.replaceWith(containerEl);

                var repositorySourceExpression = attributes['repositorySource'];
                var shouldLink = attributes['shouldLink'];

                if (!repositorySourceExpression || !repositorySourceExpression.length) {
                    throw Error('the repository source expression should be defined');
                }

                $scope.$watch(repositorySourceExpression, function(repositorySource) {

                    /**
                     * <p>Extracts the repository code from the repository source.  This could either be from a
                     * 'repositoryCode' field on the repository source or be from a repository object which is
                     * supplied with the repository source.</p>
                     */

                    function deriveRepositoryCode(rs) {
                        if(!rs) {
                            throw Error('a repository source object should be suplied');
                        }

                        var repositoryCode = rs.repository ? rs.repository.code : rs.repositoryCode;

                        if(!repositoryCode || !repositoryCode.length) {
                            throw Error('a repository code should be able to be extracted from the repository source');
                        }

                        return repositoryCode;
                    }

                    function setupForNoHyperlink() {
                        if('span' != containerEl[0].tagName.toLowerCase()) {
                            var el = angular.element('<span></span>');
                            containerEl.replaceWith(el);
                            containerEl = el;
                        }

                        breadcrumbItem = undefined;
                        containerEl.text(repositorySource ? repositorySource.code : '');
                    }

                    // we may want an anchor or we may want simple text.

                    if ((undefined == shouldLink || 'true' == shouldLink) && repositorySource && !isChildOfForm(containerEl)) {

                        userState.areAuthorized([{
                            targetType:'REPOSITORY',
                            targetIdentifier:deriveRepositoryCode(repositorySource),
                            permissionCode:'REPOSITORY_VIEW'
                        }]).then(function(flag) {

                            if(flag) {
                                breadcrumbItem = breadcrumbFactory.createViewRepositorySource(repositorySource);

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

                                    containerEl.text(repositorySource ? repositorySource.code : '');
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