/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display the transcluded (material inside the element) if the permission holds against
 * the nominated user rating.</p>
 */

angular.module('haikudepotserver').directive('showIfUserRatingPermission',[
    'userState', 'standardDirectiveMixins',
    function(userState,standardDirectiveMixins) {
        return {
            restrict: 'A',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

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
                    showOrHideElementAfterCheckPermission(
                        userState,
                        element,
                        permissionCode,
                        'USERRATING',
                        userRating ? userRating.code : undefined);
                }

                $scope.$on('userChangeSuccess', function() { check(); });

            }
        }
    }
]);