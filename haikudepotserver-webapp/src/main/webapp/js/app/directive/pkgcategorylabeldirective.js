/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small html element which identifies the category.  This
 * consists of a localized name.</p>
 */

angular.module('haikudepotserver').directive('pkgCategoryLabel',function() {
    return {
        restrict: 'E',
        template:'<message key=\"pkgCategory.{{pkgCategory.code.toLowerCase()}}.title\"></message>',
        replace: true,
        scope: {
            pkgCategory: '='
        },
        controller:
            ['$scope',
                function($scope) {

                }
            ]
    };
});