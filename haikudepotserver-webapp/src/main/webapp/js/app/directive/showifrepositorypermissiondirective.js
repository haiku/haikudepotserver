/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated repository.</p>
 */

angular.module('haikudepotserver').directive('showIfRepositoryPermission',[
    'userState', 'standardDirectiveMixins',
    function(userState,standardDirectiveMixins) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

                var repositoryExpression = attributes['repository'];
                var permissionCodeExpression = attributes['showIfRepositoryPermission'];
                var repository = $scope.$eval(repositoryExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(repositoryExpression, function(newValue) {
                    repository = newValue;
                    check();
                });

                $scope.$watch(permissionCodeExpression, function(newValue) {
                    permissionCode = newValue;
                    check();
                });

                function check() {
                    showOrHideElementAfterCheckPermission(
                        userState,
                        element,
                        permissionCode,
                        'REPOSITORY',
                        repository ? repository.code : undefined);
                }

                $scope.$on('userChangeSuccess', function() { check(); });

            }
        }
    }
]);