/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the package.</p>
 */

angular.module('haikudepotserver').directive('pkgLabel',function() {
    return {
        restrict: 'E',
        template:'<span>{{pkg.name}}</span>',
        replace: true,
        scope: {
            pkg: '='
        },
        controller:
            ['$scope',
                function($scope) {
                }
            ]
    };
});