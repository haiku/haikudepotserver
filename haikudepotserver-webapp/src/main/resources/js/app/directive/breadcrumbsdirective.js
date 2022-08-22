/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small block of HTML that represents the breadcrumbs.</p>
 */

angular.module('haikudepotserver').directive('breadcrumbs',function() {
    return {
        restrict: 'E',
        templateUrl:'/__js/app/directivetemplate/breadcrumbs.html',
        replace: true,
        scope: {
        },
        controller:
            ['$scope','$location','breadcrumbs',
                function($scope,$location,breadcrumbs) {

                    $scope.stack = breadcrumbs.stack();

                    function isItemActive(item) {
                        if(!item) {
                            return false;
                        }
                        return item.path == $location.path();
                    }

                    $scope.shouldShowItems = function() {
                        return $scope.stack &&
                                $scope.stack.length > 1;
                    };

                    $scope.goItem = function(item) {
                        breadcrumbs.popToAndNavigate(item);
                        return false;
                    };

                    $scope.isItemActive = function(item) {
                        return isItemActive(item);
                    };

                    $scope.listItemClass = function(item) {
                        if(isItemActive(item)) {
                            return [ 'active' ];
                        }

                        return [];
                    };

                    $scope.$on(
                        "breadcrumbChangeSuccess",
                        function(stack) {
                            $scope.stack = breadcrumbs.stack();
                        }
                    );

                }
            ]
    };
});
