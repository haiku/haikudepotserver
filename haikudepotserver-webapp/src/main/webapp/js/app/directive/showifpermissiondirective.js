/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds.  Note that
 * this variant does not have a specific target.</p>
 */

angular.module('haikudepotserver').directive('showIfPermission',[
    'userState', function(userState) {
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
                    if(!permissionCode) {
                        element.addClass('app-hide');
                    }
                    else {
                        var targetAndPermissions = [];

                        if(angular.isArray(permissionCode)) {
                            _.each(permissionCode, function(item) {
                                targetAndPermissions.push({
                                    targetType: null,
                                    targetIdentifier : null,
                                    permissionCode : item
                                });
                            });
                        }
                        else {
                            targetAndPermissions.push({
                                targetType: null,
                                targetIdentifier : null,
                                permissionCode : ''+permissionCode
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