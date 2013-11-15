/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to be placed into error pages and allows the user to view any error messages that are
 * related to the fields of the form.  This means that each error message does not need to have explicit coding in
 * order to display the error involved.</p>
 */

angular.module('haikudepotserver').directive('errorMessages',function() {
        return {
            restrict: 'E',
            templateUrl:'/js/app/directive/errormessages.html',
            replace: true,
            scope: {
                error:'=',
                keyPrefix:'@'
            },
            controller:
                ['$scope',
                    function($scope) {
                    }
                ]
        };
    }
);