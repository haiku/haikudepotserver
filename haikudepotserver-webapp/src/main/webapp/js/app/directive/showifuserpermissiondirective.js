/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated user.</p>
 */

angular.module('haikudepotserver').directive('showIfUserPermission',[
    'userState', 'standardDirectiveMixins',
    function(userState,standardDirectiveMixins) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

                var userExpression = attributes['user'];
                var permissionCodeExpression = attributes['showIfUserPermission'];
                var user = $scope.$eval(userExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(userExpression, function(newValue) {
                    user = newValue;
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
                        'USER',
                        user ? user.nickname : undefined);
                }

            }
        }
    }
]);