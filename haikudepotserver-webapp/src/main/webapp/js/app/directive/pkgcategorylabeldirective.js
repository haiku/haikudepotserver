/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small html element which identifies the category.  This
 * consists of a localized name.</p>
 */

angular.module('haikudepotserver').directive('pkgCategoryLabel',[
    '$log',
    'messageSource','userState','breadcrumbFactory',
    function($log,messageSource,userState,breadcrumbFactory) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var shouldLink = undefined == attributes['shouldLink'] || 'true' == shouldLink;

                var el = angular.element(shouldLink ? '<a href=""></a>' : '<span></span>');
                element.replaceWith(el);

                var pkgCategoryExpression = attributes['pkgCategory'];

                if (!pkgCategoryExpression || !pkgCategoryExpression.length) {
                    throw Error('expected expression for "pkgCategoryExpression"');
                }

                $scope.$watch(pkgCategoryExpression, function(newValue) {
                    if(newValue) {

                        // add a hyperlink to allow the user to jump to the home page with a search
                        // set to this category.

                        if(shouldLink) {
                            var fullUrl = breadcrumbFactory.toFullPath(
                                breadcrumbFactory.createHome({
                                    pkgcat: newValue.code,
                                    viewcrttyp: 'CATEGORIES'
                                })
                            );

                            el.attr('href', fullUrl);
                        }

                        messageSource.get(
                            userState.naturalLanguageCode(),
                            'pkgCategory.' + newValue.code.toLowerCase() + '.title').then(
                            function (localizedString) {
                                el.text(localizedString);
                            },
                            function () {
                                el.text('???');
                                $log.error('unable to render the pkg category label for; ' + newValue);
                            }
                        );
                    }
                    else {
                        if(shouldLink) {
                            el.attr('href','');
                        }

                        el.text('');
                    }
                });

            }
        };
    }
]);