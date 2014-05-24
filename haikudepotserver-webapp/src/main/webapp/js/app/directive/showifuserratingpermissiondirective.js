/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated user rating.</p>
 */

angular.module('haikudepotserver').directive('showIfUserRatingPermission',[
    'userState', function(userState) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                var userRatingExpression = attributes['userRating'];
                var permissionCodeExpression = attributes['showIfUserRatingPermission'];
                var userRating = $scope.$eval(userRatingExpression);
                var permissionCode = $scope.$eval(permissionCodeExpression);

                // by default we will hide it.

                element.addClass('app-hide');
                check();

                $scope.$watch(userRatingExpression, function(newValue) {
                    userRating = newValue;
                    check();
                });

                $scope.$watch(permissionCodeExpression, function(newValue) {
                    permissionCode = newValue;
                    check();
                });

                function check() {
                    if(!permissionCode || !userRating) {
                        element.addClass('app-hide');
                    }
                    else {
                        var targetAndPermissions = [];

                        if(angular.isArray(permissionCode)) {
                            _.each(permissionCode, function(item) {
                                targetAndPermissions.push({
                                    targetType: 'USERRATING',
                                    targetIdentifier : userRating.code,
                                    permissionCode : item
                                });
                            });
                        }
                        else {
                            targetAndPermissions.push({
                                targetType: 'USERRATING',
                                targetIdentifier : userRating.code,
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