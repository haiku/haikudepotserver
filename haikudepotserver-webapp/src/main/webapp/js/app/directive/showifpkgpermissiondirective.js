/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated package.</p>
 */

angular.module('haikudepotserver').directive('showIfPkgPermission',[
    'userState', 'standardDirectiveMixins',
    function(userState,standardDirectiveMixins) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

                var pkgExpression = attributes['pkg'];
                var permissionCodeExpression = attributes['showIfPkgPermission'];
                var pkg = $scope.$eval(pkgExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(pkgExpression, function(newValue) {
                    pkg = newValue;
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
                        'PKG',
                        pkg ? pkg.name : undefined);
                }

                $scope.$on('userChangeSuccess', function() { check(); });

            }
        }
    }
]);