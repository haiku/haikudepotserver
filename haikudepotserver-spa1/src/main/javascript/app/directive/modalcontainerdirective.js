/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will show a modal dialog box containing some controls or other such information.</p>
 */

angular.module('haikudepotserver').directive('modalContainer',[
        '$timeout',
        function($timeout) {
            return {
                restrict: 'E',
                templateUrl:'/__js/app/directivetemplate/modalcontainer.html',
                replace: true,
                transclude: true,
                scope: {
                    show: '=',
                    width: '@',
                    height: '@',
                    close: '@' // a function to later eval
                },
                link: function($scope, elem) {

                    // listen for the escape event here so that if the user presses escape while the
                    // model is open, the model can be closed.

                    elem.on('keyup', function(event) {
                        if(27 === event.keyCode) { // 27 = escape
                            $scope.$apply(function() {
                                $scope.goClose(event);
                            });
                        }
                    });

                    // take focus into the modal container div (you can do this with a tabindex) and then
                    // it is possible to listen out for keyup events.  This then allows you to listen for
                    // escape-key which can be used to close the modal.

                    $scope.$watch('show', function(newValue) {
                        if(newValue) {
                            $timeout(
                                function() {
                                    elem[0].focus();
                                },
                                0
                            );
                        }
                    });

                },
                controller:
                    ['$scope',
                        function($scope) {

                            $scope.style = {};
                            $scope.showModal = false;

                            function updateShowModal() {
                                $scope.showModal = $scope.style['width'] && $scope.show;
                            }

                            $scope.$watch('show', function() {
                                updateShowModal();
                            });

                            $scope.$watch('height', function(newValue) {
                                var height = parseInt('' + newValue,10);
                                $scope.style['height'] = height + 'px';
                                $scope.style['margin-top'] = '' + (height / -2.0) + 'px'; // centres it
                                updateShowModal();
                            });

                            $scope.$watch('width', function(newValue) {
                                var width = parseInt(''+newValue,10);
                                $scope.style['width'] = width + 'px';
                                $scope.style['margin-left'] = '' + (width / -2.0) + 'px'; // centres it
                                updateShowModal();
                            });

                            $scope.goClose = function(event) {
                                $scope.$parent.$eval($scope.close); // evaluates the function.
                                event.preventDefault();
                                event.stopPropagation();
                            };

                            $scope.goNoop = function(event) {
                                event.stopPropagation();
                            }

                        }
                    ]
            };
        }]
);
