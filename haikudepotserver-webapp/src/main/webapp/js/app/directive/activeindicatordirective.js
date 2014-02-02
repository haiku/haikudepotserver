/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small element that indicates if the supplied value is active; for example, a green
 * circle for active and a red circle for inactive.</p>
 */

angular.module('haikudepotserver').directive('activeIndicator',function() {
    return {
        restrict: 'E',
        template:'<div ng-class="classes">&nbsp;</div>',
        replace: true,
        scope: {
            state: '='
        },
        controller:
            ['$scope',
                function($scope) {

                    $scope.classes = ['active-indicator'];

                    $scope.$watch('state',function(newValue, oldValue) {
                        $scope.classes = [
                            'active-indicator',
                            newValue ? 'active-indicator-true' : 'inactive-indicator-false'
                        ];
                    });

                }
            ]
    };
});