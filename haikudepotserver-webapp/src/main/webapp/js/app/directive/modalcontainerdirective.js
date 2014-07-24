/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will show a modal dialog box containing some controls or other such information.</p>
 */

angular.module('haikudepotserver').directive('modalContainer',function() {
    return {
        restrict: 'E',
        templateUrl:'/js/app/directive/modalcontainer.html',
        replace: true,
        transclude: true,
        scope: {
            show: '=',
            width: '@',
            height: '@',
            close: '@' // a function to later eval
        },
        controller:
            ['$scope',
                function($scope) {

                    $scope.style = {};
                    $scope.showModal = false;

                    function updateShowModal() {
                        $scope.showModal = $scope.style['width'] && $scope.show;
                    }

                    $scope.$watch('show', function() {
                        updateShowModal();
                    });

                    $scope.$watch('height', function(newValue) {
                        var height = parseInt(''+newValue,10);
                        $scope.style['height'] = height + 'px';
                        $scope.style['margin-top'] = '' + (height/-2.0) + 'px'; // centres it
                        updateShowModal();
                    });

                    $scope.$watch('width', function(newValue) {
                        var width = parseInt(''+newValue,10);
                        $scope.style['width'] = width + 'px';
                        $scope.style['margin-left'] = '' + (width/-2.0) + 'px'; // centres it
                        updateShowModal();
                    });

                    $scope.goClose = function() {
                        $scope.$parent.$eval($scope.close); // evaluates the function.
                    }
                }
            ]
    };
});