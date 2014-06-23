/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver').directive('userLabel',[
    'standardDirectiveMixins','breadcrumbs',
    function(standardDirectiveMixins,breadcrumbs) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

                var userExpression = attributes['user'];
                var shouldLink = attributes['shouldLink'];
                var userBreadcrumbItem = undefined;

                if(!userExpression || !userExpression.length) {
                    throw 'expected expression for "user"';
                }

                var containerEl = angular.element('<span></span>');
                var textTargetEl = containerEl;
                element.replaceWith(containerEl);

                if((undefined == shouldLink || 'true' == shouldLink) && !isChildOfForm(containerEl)) {
                    var hyperlinkEl = angular.element('<a href=""></a>');
                    containerEl.append(hyperlinkEl);
                    textTargetEl = hyperlinkEl;

                    hyperlinkEl.on('click', function(el) {
                        if(userBreadcrumbItem) {
                            $scope.$apply(function() {
                                breadcrumbs.pushAndNavigate(userBreadcrumbItem);
                            });
                        }
                        el.preventDefault();
                    });
                }

                function refresh(user) {

                    if (!user) {
                        textTargetEl.text('');
                        userBreadcrumbItem = undefined;
                    }
                    else {
                        userBreadcrumbItem = breadcrumbs.createViewUser(user);
                        textTargetEl.text(user.nickname);
                    }
                }

                $scope.$watch(userExpression, function(newValue) {
                    refresh(newValue);
                });

                refresh($scope.$eval(userExpression));

            }
        }

    }]);