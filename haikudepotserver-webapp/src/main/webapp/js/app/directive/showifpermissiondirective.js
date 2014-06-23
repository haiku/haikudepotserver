/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds.  Note that
 * this variant does not have a specific target.</p>
 */

angular.module('haikudepotserver').directive('showIfPermission',[
    'userState','standardDirectiveMixins',
    function(userState,standardDirectiveMixins) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

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
                    showOrHideElementAfterCheckPermission(
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