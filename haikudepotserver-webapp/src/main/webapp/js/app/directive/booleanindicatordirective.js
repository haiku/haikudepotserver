/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text to indicate if the state
 * bound is truthy or falsey</p>
 */

angular.module('haikudepotserver').directive('booleanIndicator',[
    'messageSource','userState',
    function(messageSource,userState) {
        return {
            restrict: 'E',
            link: function ($scope, element, attributes) {

                var stateExpression = attributes['state'];

                if (!stateExpression || !stateExpression.length) {
                    throw Error('a value for the binding \'state\' was expected');
                }

                var containerE = angular.element('<span></span>');
                element.replaceWith(containerE);

                $scope.$watch(stateExpression, function (newValue) {

                    messageSource.get(
                        userState.naturalLanguageCode(),
                            'gen.' + (!!newValue ? 'yes' : 'no')
                    ).then(
                        function (str) {
                            containerE.text(str);
                        },
                        function () {
                            containerE.text('???');
                        }
                    );

                });
            }
        };
    }
]);