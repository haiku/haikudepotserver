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
                '$rootScope','$scope','$log','$location','$route',
                'userState','referenceData','messageSource',
                function(
                    $rootScope,$scope,$log,$location,$route,
                    userState,referenceData,messageSource) {

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

                    // -----------------
                    // GENERAL

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        $location.path('/').search({});
                        return false;
                    };

                    $scope.canGoMore = function() {
                        var p = $location.path();
                        return '/error' != p && '/about' != p;
                    };

                    // This will take the user to a page about the application.

                    $scope.goMore = function() {
                        $location.path('/about').search({});
                        $scope.showActions = false;
                        return false;
                    };

                    // -----------------
                    // NATURAL LANGUAGES

                    /**
                     * <p>This gets hit when the user chooses a language from the user interface's drop-down.</p>
                     */

                    $scope.$watch(
                        'naturalLanguageData.selectedNaturalLanguageOption',
                        function(newValue) {
                            if(!!newValue && !userState.user()) {
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
                        $location.path('/repositories').search({});
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
                        $location.path('/user/'+userState.user().nickname).search({});
                        $scope.showActions = false;
                    };

                    $scope.goLogout = function() {
                        userState.user(null);
                        $location.path('/').search({});
                        $scope.showActions = false;
                    };

                    // -----------------
                    // USER RELATED, BUT NOT CURRENTLY AUTHENTICATED

                    $scope.canAuthenticateOrCreate = function() {
                        return !isLocationPathDisablingUserState() && !userState.user();
                    };

                    $scope.goAuthenticate = function() {
                        var p = $location.path();
                        $location.path('/authenticateuser').search(
                            _.extend($location.search(), { destination: p }));
                        $scope.showActions = false;
                    };

                    $scope.goCreateUser = function() {
                        $location.path('/users/add').search({});
                        $scope.showActions = false;
                    };

                    // -----------------
                    // EVENT HANDLING

                    // when the page changes, the actions may change; for example, it is not appropriate to
                    // show the 'login' option when the user is presently logging in.

//                    $rootScope.$on(
//                        "$locationChangeSuccess",
//                        function(event, next, current) {
//                        }
//                    );

                    // when the user logs in or out then the actions may also change; for example, it makes
                    // no sense to show the logout button if nobody is presently logged in.

                    $rootScope.$on(
                        "userChangeSuccess",
                        function() {
                            $scope.userNickname = userState.user() ? userState.user().nickname : undefined;
                        }
                    );

                    // when the natural language changes; maybe because of user choice, default or the user
                    // login | logout, we need to reflect this change in the banner indicator.

                    $rootScope.$on(
                        "naturalLanguageChange",
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