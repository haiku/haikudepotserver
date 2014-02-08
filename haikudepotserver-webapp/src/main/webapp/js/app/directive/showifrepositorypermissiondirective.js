/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated repository.</p>
 */

angular.module('haikudepotserver').directive('showIfRepositoryPermission',[
    'userState', function(userState) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                var repositoryExpression = attributes['repository'];
                var permissionCodeExpression = attributes['showIfRepositoryPermission'];
                var repository = $scope.$eval(repositoryExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(repositoryExpression, function(newValue,oldValue) {
                    repository = newValue;
                    check();
                });

                $scope.$watch(permissionCodeExpression, function(newValue,oldValue) {
                    permissionCode = newValue;
                    check();
                });

                function check() {
                    if(!permissionCode || !repository) {
                        element.addClass('app-hide');
                    }
                    else {
                        var targetAndPermissions = [];

                        if(angular.isArray(permissionCode)) {
                            _.each(permissionCode, function(item) {
                                targetAndPermissions.push({
                                    targetType: 'REPOSITORY',
                                    targetIdentifier : repository.code,
                                    permissionCode : item
                                });
                            });
                        }
                        else {
                            targetAndPermissions.push({
                                targetType: 'REPOSITORY',
                                targetIdentifier : repository.code,
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