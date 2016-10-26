/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to show an indication of a rating value.  The value is intended to be a value between
 * zero and five (inclusive).  The value may be a floating point number, in which case half stars may be shown as
 * well.</p>
 */

angular.module('haikudepotserver').directive('ratingIndicator',[
        function() {
            return {
                restrict: 'E',
                link : function($scope,element,attributes) {

                    // setup by creating a number of star images.

                    var topLevelE = angular.element('<span class="rating-indicator"></div>');
                    var starEs = [];
                    var extension = Modernizr.svg ? 'svg' : 'png';

                    element.replaceWith(topLevelE);

                    for(var i=0;i<5;i++) {
                        var starE = angular.element('<img width="16" height="16" src="/__img/staroff.'+extension+'"/>');
                        starEs.push(starE);
                        topLevelE.append(starE);
                    }

                    // given the value supplied (a numeric between 0 and 5 inclusive), this
                    // function will update the necessary stars.

                    function refreshStars(rating) {

                        if(undefined==rating || null==rating) {
                            topLevelE.addClass('app-hide');
                        }
                        else {

                            topLevelE.removeClass('app-hide');

                            if (!angular.isNumber(rating)) {
                                throw Error('the value supplied is not a number');
                            }

                            if (rating < 0) {
                                throw Error('the value supplied for a rating indicator is less than zero');
                            }

                            if (rating > 5) {
                                throw Error('the value supplied for a rating indicator is more than five');
                            }

                            rating *= 2;

                            for (var i = 2; i <= 10; i += 2) {
                                var starE = starEs[(i / 2) - 1];

                                if (rating >= i) {
                                    starE.attr('src', '/__img/staron.'+extension);
                                }
                                else {
                                    if (rating >= i - 1) {
                                        starE.attr('src', '/__img/starhalf.'+extension);
                                    }
                                    else {
                                        starE.attr('src', '/__img/staroff.'+extension);
                                    }
                                }
                            }
                        }
                    }

                    // looks to see what the bound value is on the 'rating' expression and then
                    // uses that value to configure the stars.

                    function refreshEvaluatedValue() {
                        var ratingExpression = attributes['rating'];
                        var rating = 0;

                        if(angular.isDefined(ratingExpression) && ratingExpression.length) {
                            rating = $scope.$eval(ratingExpression);
                        }

                        refreshStars(rating);
                    }

                    attributes.$observe(
                        'rating',
                        function() {
                            var ratingExpression = attributes['rating'];

                            if(angular.isDefined(ratingExpression) && ratingExpression.length) {
                                $scope.$watch(ratingExpression, function(newValue) {
                                    refreshStars(newValue);
                                });
                            }

                            refreshEvaluatedValue();
                        }
                    );

                    refreshEvaluatedValue();

                }
            };
        }
    ]
);