/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive renders a small piece of text and maybe a hyperlink to show a user.</p>
 */

angular.module('haikudepotserver').directive('userLabel',[
    'standardDirectiveFunctions','breadcrumbs','userState','breadcrumbFactory',
    function(standardDirectiveFunctions,breadcrumbs,userState,breadcrumbFactory) {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                var userExpression = attributes['user'];
                var shouldLink = undefined == attributes['shouldLink'] || 'true' == shouldLink;

                if(!userExpression || !userExpression.length) {
                    throw Error('expected expression for "user"');
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

                        if(shouldLink && !standardDirectiveFunctions.isChildOfForm(containerEl)) {

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

                                        var item = breadcrumbFactory.createViewUser(user);

                                        anchorEl.on('click', function(event) {
                                            event.preventDefault();
                                            $scope.$apply(function() {
                                                breadcrumbs.pushAndNavigate(item);
                                            });
                                        });

                                        anchorEl.attr(
                                            'href',
                                            breadcrumbFactory.toFullPath(item)
                                        );

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