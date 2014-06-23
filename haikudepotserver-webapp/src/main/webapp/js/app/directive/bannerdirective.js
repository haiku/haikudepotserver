/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render the bar at the top of the screen that displays what this is and other details about
 * your usage of the application; maybe language, who is logged in and so on.</p>
 */

angular.module('haikudepotserver').directive('banner',function() {
    return {
        restrict: 'E',
        templateUrl:'/js/app/directive/banner.html',
        replace: true,
        controller:
            [
                '$rootScope','$scope','$log','$location','$route','$window',
                'userState','referenceData','messageSource','breadcrumbs',
                function(
                    $rootScope,$scope,$log,$location,$route,$window,
                    userState,referenceData,messageSource,breadcrumbs) {

                    $scope.showActions = false;
                    $scope.userNickname = undefined;
                    $scope.naturalLanguageData = {
                        naturalLanguageCode : userState.naturalLanguageCode(),
                        naturalLanguageOptions : undefined,
                        selectedNaturalLanguageOption : undefined
                    };

                    function isLocationPathDisablingUserState() {
                        var p = $location.path();
                        return '/error' == p || '/authenticateuser' == p || '/createuser' == p;
                    }

                    $scope.canShowBannerActions = function() {
                        var p = $location.path();
                        return '/error' != p;
                    }

                    // -----------------
                    // GENERAL

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome()
                        ]);
                        return false;
                    };

                    $scope.canGoMore = function() {
                        var p = $location.path();
                        return '/error' != p && '/about' != p;
                    };

                    // This will take the user to a page about the application.

                    $scope.goMore = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.createAbout()
                        ]);
                        $scope.showActions = false;
                        return false;
                    };

                    // -----------------
                    // NATURAL LANGUAGES

                    $scope.canChooseNaturalLanguage = function() {
                        var p = $location.path();
                        return !!$scope.naturalLanguageData.naturalLanguageOptions &&
                            '/error' != p;
                    }

                    /**
                     * <p>This gets hit when the user chooses a language from the user interface's drop-down.</p>
                     */

                    $scope.$watch(
                        'naturalLanguageData.selectedNaturalLanguageOption',
                        function(newValue) {
                            if(!!newValue) {
                                userState.naturalLanguageCode(newValue.code);
                                $scope.showActions = false;
                            }
                        }
                    );

                    /**
                     * <p>This function will get the natural language code chosen in the user state and will make sure
                     * that the selected language option reflects this.</p>
                     */

                    function updateSelectedNaturalLanguageOption() {
                        $scope.naturalLanguageData.selectedNaturalLanguageOption = _.findWhere(
                            $scope.naturalLanguageData.naturalLanguageOptions,
                            { code : userState.naturalLanguageCode() }
                        );
                    }

                    function updateNaturalLanguageOptionsTitles() {
                        _.each($scope.naturalLanguageData.naturalLanguageOptions, function(nl) {
                            messageSource.get(userState.naturalLanguageCode(), 'naturalLanguage.' + nl.code).then(
                                function(value) {
                                    nl.title = value;
                                },
                                function() {
                                    $log.error('unable to get the localized name for the natural language \''+nl.code+'\'');
                                }
                            );
                        });
                    }

                    referenceData.naturalLanguages().then(
                        function(data) {
                            $scope.naturalLanguageData.naturalLanguageOptions = _.map(data, function(d) {
                                return {
                                    code : d.code,
                                    title : d.name
                                };
                            });

                            updateNaturalLanguageOptionsTitles();
                            updateSelectedNaturalLanguageOption();
                        },
                        function() {
                            $location.path('/error').search({});
                        }
                    );

                    // -----------------
                    // HIDE AND SHOW ACTIONS

                    $scope.goHideActions = function() {
                        $scope.showActions = false;
                    };

                    $scope.goShowActions = function() {
                        $scope.showActions = true;
                    };

                    // -----------------
                    // REPOSITORY

                    // note that this is also protected by a permission which is enforced in the template.

                    $scope.canShowRepository = function() {
                        var p = $location.path();
                        return '/error' != p && '/repositories' != p;
                    }

                    $scope.goListRepositories = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.createListRepositories()
                        ]);
                        $scope.showActions = false;
                    }

                    // -----------------
                    // AUTHENTICATED USER RELATED

                    $scope.hasAuthenticatedUser = function() {
                        return !!userState.user();
                    };

                    $scope.canShowAuthenticatedUser = function() {
                        return !isLocationPathDisablingUserState() && $scope.hasAuthenticatedUser();
                    };

                    $scope.goViewUser = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.createViewUser(userState.user())
                        ]);
                        $scope.showActions = false;
                    };

                    $scope.goLogout = function() {
                        userState.user(null);
                        breadcrumbs.reset();
                        $window.location.href='/';
                        $scope.showActions = false;
                    };

                    // -----------------
                    // USER RELATED, BUT NOT CURRENTLY AUTHENTICATED

                    $scope.canAuthenticateOrCreate = function() {
                        return !isLocationPathDisablingUserState() && !userState.user();
                    };

                    $scope.goAuthenticate = function() {
                        breadcrumbs.pushAndNavigate(breadcrumbs.createAuthenticate());
                        $scope.showActions = false;
                    };

                    $scope.goCreateUser = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.createAddUser()
                        ]);
                        $scope.showActions = false;
                    };

                    // not from a permissions perspective, but from a navigational perspective.
                    $scope.canGoListUsers = function() {
                        var p = $location.path();
                        return '/users' != p;
                    }

                    $scope.goListUsers = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbs.createHome(),
                            breadcrumbs.createListUsers()
                        ]);
                        $scope.showActions = false;
                    }

                    // -----------------
                    // EVENT HANDLING

                    // when the user logs in or out then the actions may also change; for example, it makes
                    // no sense to show the logout button if nobody is presently logged in.

                    $scope.$on(
                        'userChangeSuccess',
                        function() {
                            $scope.userNickname = userState.user() ? userState.user().nickname : undefined;
                        }
                    );

                    // when the natural language changes; maybe because of user choice, default or the user
                    // login | logout, we need to reflect this change in the banner indicator.

                    $scope.$on(
                        'naturalLanguageChange',
                        function() {
                            $scope.naturalLanguageData.naturalLanguageCode = userState.naturalLanguageCode();
                            updateNaturalLanguageOptionsTitles();
                            updateSelectedNaturalLanguageOption();
                        }
                    );

                }
            ]
    };
});