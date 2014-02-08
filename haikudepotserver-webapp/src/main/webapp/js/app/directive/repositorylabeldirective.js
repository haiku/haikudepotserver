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
        template:'<a href=\"\" ng-click=\"goView()\">{{repository.code}}</a>',
        replace: true,
        scope: {
            repository: '='
        },
        controller:
            ['$scope','$location',
                function($scope,$location) {

                    $scope.goView = function() {
                        $location.path('/viewrepository/'+$scope.repository.code).search({});
                    }
                }
            ]
    };
});