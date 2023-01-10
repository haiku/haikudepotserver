/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds.  Note that
 * this variant does not have a specific target.</p>
 */

angular.module('haikudepotserver').directive('showIfPermission',[
    'userState','standardDirectiveFunctions',
    function(userState,standardDirectiveFunctions) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                var permissionCodeExpression = attributes['showIfPermission'];
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(permissionCodeExpression, function(newValue) {
                    permissionCode = newValue;
                    check();
                });

                function check() {
                    standardDirectiveFunctions.showOrHideElementAfterCheckPermission(
                        userState,
                        element,
                        permissionCode,
                        null,
                        null);
                }

                $scope.$on('userChangeSuccess', function() { check(); });

            }
        }
    }
]);