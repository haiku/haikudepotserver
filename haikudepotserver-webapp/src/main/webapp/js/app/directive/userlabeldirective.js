/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver').directive('userLabel',function() {
    return {
        restrict: 'E',
        link : function($scope,element,attributes) {

            var userExpression = attributes['user'];

            if(!userExpression || !userExpression.length) {
                throw 'expected expression for "user"';
            }

            var containerEl = angular.element('<span></span>');
            element.replaceWith(containerEl);

            function refresh(user) {
                containerEl.text(user ? user.nickname : '');
            }

            $scope.$watch(userExpression, function(newValue) {
                refresh(newValue);
            });

            refresh($scope.$eval(userExpression));

        }
    };

});