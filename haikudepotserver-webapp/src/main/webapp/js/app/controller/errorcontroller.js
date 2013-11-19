/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ErrorController',
    [
        '$scope','$log','$location','userState',
        function(
            $scope,$log,$location,userState) {
               userState.user(null);
        }
    ]
);