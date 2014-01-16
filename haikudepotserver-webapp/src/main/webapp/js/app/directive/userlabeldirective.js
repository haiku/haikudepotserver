/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver').directive('userLabel',function() {
    return {
        restrict: 'E',
        template:'<span>{{user.nickname}}</span>',
        replace: true,
        scope: {
            user: '='
        },
        controller:
            ['$scope',
                function($scope) {
                }
            ]
    };
});