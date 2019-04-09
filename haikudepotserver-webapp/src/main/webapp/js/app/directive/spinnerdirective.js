/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render a DIV that covers the whole page with a spinner inside it.  This provides two
 * functions; the first is to indicate to the user that something is going on and they ought to wait a moment.
 * The second function is to cover the UI so that the user is not able to interact with the page while the
 * operation is being undertaken.</p>
 */

angular.module('haikudepotserver').directive('spinner', [
        '$timeout', 'constants',
        function($timeout, constants) {
            return {
                restrict: 'E',
                link : function($scope, element, attributes) {

                    var spinExpr = attributes['spin'];
                    var timeoutHandle = undefined;

                    var containerEl = angular.element('<div id="spinner-container" class="modal-backdrop-container app-hide"></div>');
                    var innerContainerEl = angular.element('<div></div>');
                    containerEl.append(innerContainerEl);
                    element.replaceWith(containerEl);

                    function setupSpin(flag) {
                        if (flag) {

                            if (Modernizr.svg) {
                                // blend in the SVG

                                var ns = "http://www.w3.org/2000/svg";
                                var svgEl = angular.element('<svg width="120" height="24" class="spinner-img"></svg>');

                                for (var i = 0; i < 3; i++) {
                                    var circle = document.createElementNS(ns, "circle");
                                    circle.setAttributeNS(null, 'cy', '' + 12);
                                    circle.setAttributeNS(null, 'cx', '' + (12 + (i * 48)));
                                    circle.setAttributeNS(null, 'r', '' + 11);
                                    circle.setAttributeNS(null, 'class', 'spinner-img-c' + i);
                                    svgEl[0].appendChild(circle);
                                }

                                innerContainerEl.append(svgEl);
                            }
                            else {
                                innerContainerEl.append(angular.element('<img src="/__img/spinner.gif">'));
                            }

                            containerEl.removeClass('app-hide');
                        }
                        else {
                            innerContainerEl.empty();
                            containerEl.addClass('app-hide');
                        }
                    }

                    // this will setup a timer so that there is a slight delay before the spinner appears.  This
                    // will prevent the spinner from flashing on the screen.

                    $scope.$watch(spinExpr, function (newValue) {

                        if (timeoutHandle) {
                            $timeout.cancel(timeoutHandle);
                            timeoutHandle = undefined;
                        }

                        if (newValue) {
                            timeoutHandle = $timeout(
                                function() {
                                    timeoutHandle = undefined;
                                    setupSpin(true);
                                },
                                constants.DELAY_SPINNER
                            );
                        }
                        else {
                            setupSpin(false);
                        }

                    });

                }

            };
        }]
);