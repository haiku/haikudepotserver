/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This is just to test out and experiment with the pagination control.</p>
 */

angular.module('haikudepotserver').controller(
    'PaginationControlPlayground',
    [
        '$scope',
        function(
            $scope) {

            $scope.editingValues = {
                max : 5,
                offset : 10,
                total : 100
            };

            $scope.actualValues = angular.copy($scope.editingValues);

            $scope.goSetValues = function () {
                $scope.actualValues = angular.copy($scope.editingValues);
            }

        }
    ]
);