/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated user.</p>
 */

angular.module('haikudepotserver').directive('showIfUserPermission',[
    'userState', function(userState) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

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
                    if(!permissionCode || !user) {
                        element.addClass('app-hide');
                    }
                    else {
                        var targetAndPermissions = [];

                        if(angular.isArray(permissionCode)) {
                            _.each(permissionCode, function(item) {
                                targetAndPermissions.push({
                                    targetType: 'USER',
                                    targetIdentifier : user.nickname,
                                    permissionCode : item
                                });
                            });
                        }
                        else {
                            targetAndPermissions.push({
                                targetType: 'USER',
                                targetIdentifier : user.nickname,
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