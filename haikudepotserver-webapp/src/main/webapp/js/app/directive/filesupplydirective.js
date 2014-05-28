/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is used on input elements of type "file" in order that they can relay the selected back into
 * the controller.  This way it is possible for a java-script controller to manage the File object that has been
 * chosen.</p>
 */

angular.module('haikudepotserver').directive(
    'fileSupply',[
        // no injections
        function() {
            return {
                require: 'ngModel',
                restrict: 'A',
                replace: true,
                link: function(scope, elem, attrs, ngModel) {

                    elem.on('change', function() {
                        scope.$apply(function() {
                            ngModel.$setViewValue(elem[0].files[0]);
                        });
                    });

                    scope.$watch(
                        function() {
                            return ngModel.$viewValue
                        },
                        function(oldValue, newValue) {
                            if(!oldValue && newValue) {
                                elem.val('');
                            }
                        }
                    );
                }
            };
        }]
);