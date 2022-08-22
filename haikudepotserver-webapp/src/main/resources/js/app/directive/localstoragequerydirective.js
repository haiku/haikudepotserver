/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will show an on-page query indicating the user should
 * choose if they would like to store data pertaining to the use of the
 * HDS system locally or not.</p>
 */

angular.module('haikudepotserver').directive('localStorageQuery',function() {
    return {
        restrict: 'E',
        templateUrl:'/__js/app/directivetemplate/localstoragequery.html',
        replace: true,
        scope: {
        },
        controller:
            [
                '$scope', '$log', 'localStorageProxy',
                function(
                    $scope, $log, localStorageProxy
                ) {

                    $scope.shouldShowQuery = function() {
                        return null === localStorageProxy.isLocalStorageAllowed();
                    };

                    $scope.goAllow = function() {
                        localStorageProxy.setLocalStorageAllowed(true);
                    };

                    $scope.goDisallow = function() {
                        localStorageProxy.setLocalStorageAllowed(false);
                    };

                    $scope.goInformation = function() {
                        $log.info('show more information');
                    }

                }
            ]
    };
});
