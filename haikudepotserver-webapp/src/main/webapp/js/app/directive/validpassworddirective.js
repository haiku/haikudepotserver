/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will check to make sure that the password entered in is in the correct format, is long enough
 * and is complex enough.
 */

angular.module('haikudepotserver').directive('validPassword',function() {
    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope,elem, attr, ctrl) { // ctrl = ngModel

            ctrl.$parsers.unshift(function(value) {

                var valid = (value.length >= 8)
                    && value.replace(/[^0-9]/g,'').length >= 2
                    && value.replace(/[^A-Z]/g,'').length >= 1;

                ctrl.$setValidity('validPassword', valid);
                return valid ? value : undefined;
            });

        }
    };
});