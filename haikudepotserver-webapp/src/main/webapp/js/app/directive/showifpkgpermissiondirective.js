/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated package.</p>
 */

angular.module('haikudepotserver').directive('showIfPkgPermission',[
    'userState', function(userState) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                var pkgExpression = attributes['pkg'];
                var permissionCodeExpression = attributes['showIfPkgPermission'];
                var pkg = $scope.$eval(pkgExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(pkgExpression, function(newValue,oldValue) {
                   pkg = newValue;
                    check();
                });

                $scope.$watch(permissionCodeExpression, function(newValue,oldValue) {
                    permissionCode = newValue;
                    check();
                });

                function check() {
                    if(!permissionCode || !pkg) {
                        element.addClass('app-hide');
                    }
                    else {
                        var targetAndPermissions = [];

                        if(angular.isArray(permissionCode)) {
                            _.each(permissionCode, function(item) {
                                targetAndPermissions.push({
                                    targetType: 'PKG',
                                    targetIdentifier : pkg.name,
                                    permissionCode : item
                                });
                            });
                        }
                        else {
                            targetAndPermissions.push({
                                targetType: 'PKG',
                                targetIdentifier : pkg.name,
                                permissionCode : permissionCode
                            });
                        }

                        userState.areAuthorized(targetAndPermissions).then(function(flag) {
                            if(flag) {
                                element.removeClass('app-hide');
                            }
                            else {
                                element.addClass('app-hide');
                            }
                        });
                    }
                }

            }
        }
    }
]);