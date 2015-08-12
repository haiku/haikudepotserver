/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive can be added to an input field.  It will ensure that once the system is reset to the home
 * page that the field re-gains focus.</p>
 */

angular.module('haikudepotserver').directive(
    'focusAfterResetToHome',[
        '$timeout',
        function($timeout) {
            return {
                restrict: 'A',
                replace: true,
                link: function($scope, elem) {
                    $scope.$on('didResetToHome', function() {
                        $timeout(function() {
                            elem[0].focus();
                        },0);
                    });
                }
            };
        }
    ]
);

didResetToHome