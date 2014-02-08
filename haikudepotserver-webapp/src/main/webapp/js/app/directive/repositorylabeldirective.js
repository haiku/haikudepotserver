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
        templateUrl:'/js/app/directive/repositorylabel.html',
        replace: true,
        scope: {
            repository: '='
        },
        controller:
            ['$scope','$location','userState',
                function($scope,$location,userState) {

                    $scope.canView = false;

                    $scope.$watch('repository',function(newValue,oldValue) {
                        if(!newValue) {
                            $scope.canView = false;
                        }
                        else {
                            userState.areAuthorized([{
                                    targetType:'REPOSITORY',
                                    targetIdentifier:newValue.code,
                                    permissionCode:'REPOSITORY_VIEW'
                                }]).then(function(flag) {
                                $scope.canView = flag;
                            });
                        }
                    })

                    $scope.goView = function() {
                        $location.path('/viewrepository/'+$scope.repository.code).search({});
                    }
                }
            ]
    };
});