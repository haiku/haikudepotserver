/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive takes a string, a search expression and a search expression type.  It will then render
 * that string with 'span' tags and will add a class to any occurrences of the search expression within the
 * text.</p>
 */

angular.module('haikudepotserver').directive('highlightedText',[
    'searchMixins',
    function(searchMixins) {
    return {
        restrict: 'E',
        link : function($scope,element,attributes) {

            var value = undefined;
            var valueLowerCase = undefined;
            var searchExpression = undefined;
            var searchExpressionType = undefined;

            var valueExpression = attributes['value'];
            var searchExpressionExpression = attributes['searchExpression'];
            var searchExpressionTypeExpression = attributes['searchExpressionType'];

            if(!valueExpression || !valueExpression.length) {
                throw Error('the value expression must be supplied');
            }

            if(!searchExpressionExpression || !searchExpressionExpression.length) {
                throw Error('the search expression expression must be supplied');
            }

            if(!searchExpressionTypeExpression || !searchExpressionTypeExpression.length) {
                throw Error('the search expression type expression must be supplied');
            }

            var containerE = angular.element('<span></span>');
            element.replaceWith(containerE);

            function updateText() {

                containerE.children().remove();

                if(value &&
                    value.length &&
                    searchExpression &&
                    searchExpression.length &&
                    searchExpressionType &&
                    searchExpressionType.length) {

                    searchExpression = searchExpression.toLowerCase();

                    function searchAndAppend(upto) {

                        var found = searchMixins.nextMatchSearchExpression(
                            valueLowerCase,upto,
                            searchExpression,searchExpressionType);

                        if(-1==found.offset) {
                            if(0==upto) {
                                containerE.text(value || '');
                            }
                            else {
                                containerE.append(angular.element('<span>' + _.escape(value.substring(upto,value.length)) + '</span>'));
                            }
                        }
                        else {
                            if(0==upto) {
                                containerE.text('');
                            }

                            if(upto<found.offset) {
                                containerE.append(angular.element('<span>' + _.escape(value.substring(upto,found.offset)) + '</span>'));
                            }

                            containerE.append(angular.element('<span class="highlighted">' + _.escape(value.substring(found.offset,found.offset + found.length)) + '</span>'));

                            searchAndAppend(found.offset + found.length);
                        }
                    }

                    searchAndAppend(0);

                }
                else {
                    containerE.text(value || '');
                }
            }

            $scope.$watch(valueExpression, function(newValue) {
                value = newValue;
                valueLowerCase = value.toLowerCase();
                updateText();
            });

            $scope.$watch(searchExpressionExpression, function(newValue) {
                searchExpression = newValue;
                updateText();
            });

            $scope.$watch(searchExpressionTypeExpression, function(newValue) {
                searchExpressionType = newValue;
                updateText();
            });

        }
    };
}]);