/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render a breadcrumb item as a hyperlink.  It is expected that the breadcrumb item is
 * already on the stack somewhere so that clicking on the breadcrumb will drop back to that one and show it.</p>
 */

angular.module('haikudepotserver').directive(
    'breadcrumbsStackItemLink',[
        'breadcrumbFactory', 'breadcrumbs',
        function(breadcrumbFactory, breadcrumbs) {
            return {
                restrict: 'A',
                link: function($scope, elem, attributes) {

                    var itemExpr = attributes['breadcrumbsStackItemLink'];
                    var item = undefined;

                    elem.on('click', function(event) {
                        if(0 == event.button) { // left button only.
                            event.preventDefault();

                            $scope.$apply(function () {
                                breadcrumbs.popToAndNavigate(item);
                            });
                        }
                    });

                    $scope.$watch(itemExpr, function(newValue) {

                        item = newValue;

                        if(!item) {
                            elem.attr('href','');
                        }
                        else {
                            elem.attr('href', breadcrumbFactory.toFullPath(item));
                        }

                    });

                }
            };
        }
    ]
);