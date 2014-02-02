/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small span which briefly describes the repository.</p>
 */

angular.module('haikudepotserver').directive('repositoryLabel',function() {
    return {
        restrict: 'E',
        template:'<span>{{repository.code}}</span>',
        replace: true,
        scope: {
            repository: '='
        },
        controller:
            ['$scope',
                function($scope) {
                }
            ]
    };
});