/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a version object (with the major, minor etc... values
 * into a block of HTML that is supposed to be a simple and versatile block of text
 * that describes the label.</p>
 */

angular.module('haikudepotserver').directive('versionLabel',function() {
    return {
        restrict: 'E',
        templateUrl:'/js/app/directive/versionlabel.html',
        replace: true,
        scope: {
            version: '='
        },
        controller:
            ['$scope',
                function($scope) {
                }
            ]
    };
});