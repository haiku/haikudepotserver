/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to display an error message that is provided from the application using the 'misc' API.
 * The specific error message is indicated by providing a 'key' value.  Parameter values such as {0} can be provided
 * by an expression that is supplied on the attribute 'parameters'.
 * </p>
 */

angular.module('haikudepotserver').directive('message',[
    '$log','$rootScope','messageSource','userState',
    function($log,$rootScope,messageSource,userState) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                var parametersExpr = attributes['parameters'];

                function setValue(value) {
                    var valueAssembled = value ? value : '';
                    var parameters = undefined;

                    if(parametersExpr && parametersExpr.length && -1!=valueAssembled.indexOf('{')) {
                        parameters = $scope.$eval(parametersExpr);

                        if(null!=parameters && undefined!=parameters) {

                            if(!_.isArray(parameters)) {
                                parameters = [ parameters ];
                            }

                            for(var i=0;i<parameters.length;i++) {
                                valueAssembled = valueAssembled.replace('{'+i+'}',''+parameters[i]);
                            }

                        }
                    }

                    element.text(valueAssembled);
                }

                function updateValue(key) {
                    messageSource.get(userState.naturalLanguageCode(), key).then(
                        function(value) {

                            // We need to re-check against the key because the key may have changed in the interim
                            // and the inbound promise fulfillment may not match what is actually presently required.
                            // Essentially this is a race condition.

                            if(key == attributes['key']) {
                                if (!value) {
                                    $log.warn('undefined message key; ' + key);
                                    setValue(key);
                                }
                                else {
                                    setValue(value);
                                }
                            }
                        },
                        function() { // error already logged
                            setValue('???');
                        });
                }

                setValue('...');
                updateValue(attributes['key']);

                // keeps track of when the key changes.

                attributes.$observe(
                    'key',
                    function() {
                        updateValue(attributes['key']);
                    }
                );

                $rootScope.$on(
                    "naturalLanguageChange",
                    function() {
                        updateValue(attributes['key']);
                    }
                );

                // when the input parameters for localized strings containing {0}, {1} etc... change then we need to
                // know about this so that the message can also change.

                if(parametersExpr) {
                    $scope.$watchCollection(
                        parametersExpr,
                        function() {
                            updateValue(attributes['key']);
                        }
                    );
                }
            }
        };
    }
]
);