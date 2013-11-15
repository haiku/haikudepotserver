/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display an error message that is provided from the application using the 'misc' API.
 * The specific error message is indicated by providing a 'key' value.</p>
 */

angular.module('haikudepotserver').directive('message',function() {
        return {
            restrict: 'E',
            template:'<span>{{messageValue}}</span>',
            replace: true,
            scope: {
                key:'@'
            },
            controller:
                ['$scope','$log','messageSource',
                    function($scope,$log,messageSource) {

                        $scope.messageValue = '...';

                        $scope.$watch('key',function() {
                            if($scope.key) {
                                messageSource.get($scope.key).then(
                                    function(value) {
                                        if(null==value) {
                                            $log.warn('undefined message key; '+$scope.key);
                                            $scope.messageValue=$scope.key;
                                        }
                                        else {
                                            $scope.messageValue = value;
                                        }
                                    },
                                    function() {
                                        $scope.messageValue = '???';
                                    });
                            }
                        });

                    }
                ]
        };
    }
);