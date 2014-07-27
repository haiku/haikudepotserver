/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small html element which identifies the category.  This
 * consists of a localized name.</p>
 */

angular.module('haikudepotserver').directive('pkgCategoryLabel',[
    '$log',
    'messageSource','userState',
    function($log,messageSource,userState) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var el = angular.element('<span></span>');
                element.replaceWith(el);

                var pkgCategoryExpression = attributes['pkgCategory'];

                if (!pkgCategoryExpression || !pkgCategoryExpression.length) {
                    throw Error('expected expression for "pkgCategoryExpression"');
                }

                $scope.$watch(pkgCategoryExpression, function(newValue) {
                    if(newValue) {
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
                        el.text('');
                    }
                });

            }
        };
    }
]);