/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver').directive('userLabel',[
    'standardDirectiveMixins','breadcrumbs','userState','breadcrumbFactory',
    function(standardDirectiveMixins,breadcrumbs,userState,breadcrumbFactory) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                // apply a mixin for standard directive mixins.
                angular.extend(this,standardDirectiveMixins);

                var userExpression = attributes['user'];
                var shouldLink = undefined == attributes['shouldLink'] || 'true' == shouldLink;

                if(!userExpression || !userExpression.length) {
                    throw 'expected expression for "user"';
                }

                var containerEl = angular.element('<span></span>');
                element.replaceWith(containerEl);

                function refresh(user) {

                    if (!user) {
                        containerEl.children().remove();
                        containerEl.text('');
                    }
                    else {

                        function setupNonLinking() {
                            containerEl.children().remove();
                            containerEl.text(user.nickname);
                        }

                        if(shouldLink && !isChildOfForm(containerEl)) {

                            userState.areAuthorized([{
                                targetType:'USER',
                                targetIdentifier:user.nickname,
                                permissionCode:'USER_VIEW'
                            }]).then(
                                function(flag) {
                                    if(flag) {

                                        // now we need to configure a hyperlink to view the user.

                                        var anchorEl = angular.element('<a href=""></a>');
                                        anchorEl.text(user.nickname);
                                        containerEl.text('');
                                        containerEl.append(anchorEl);

                                        anchorEl.on('click', function(event) {
                                            event.preventDefault();
                                            $scope.$apply(function() {
                                                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewUser(user));
                                            });
                                        });

                                    }
                                    else {
                                        setupNonLinking();
                                    }
                                },
                                function() {
                                    setupNonLinking(); // should this perhaps be throwing an exception instead?
                                }
                            );

                        }
                        else {
                            setupNonLinking();
                        }

                    }
                }

                $scope.$watch(userExpression, function(newValue) {
                    refresh(newValue);
                });

            }
        }

    }
]);