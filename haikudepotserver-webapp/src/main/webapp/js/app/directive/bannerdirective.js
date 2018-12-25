/*
 * Copyright 2013-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will render the bar at the top of the screen that displays what this is and other details about
 * your usage of the application; maybe language, who is logged in and so on.</p>
 */

angular.module('haikudepotserver').directive('banner',function() {
    return {
        restrict: 'E',
        templateUrl:'/__js/app/directive/banner.html',
        replace: true,
        scope: {
        },
        controller:
            [
                '$rootScope', '$scope', '$log', '$location', '$route', '$window',
                'userState', 'referenceData', 'messageSource', 'breadcrumbs',
                'errorHandling', 'breadcrumbFactory', 'jsonRpc', 'constants',
                'runtimeInformation',
                function(
                    $rootScope, $scope, $log, $location, $route, $window,
                    userState, referenceData, messageSource, breadcrumbs,
                    errorHandling, breadcrumbFactory, jsonRpc, constants,
                    runtimeInformation) {

                    $scope.showActions = false;
                    $scope.showWarnNonProduction = undefined;
                    $scope.userNickname = undefined;
                    $scope.naturalLanguageCode = userState.naturalLanguageCode();

                    function refreshShowWarnNonProduction() {
                        runtimeInformation.getRuntimeInformation().then(
                            function (result) {
                                $scope.showWarnNonProduction = !result.isProduction;
                            });
                    }

                    refreshShowWarnNonProduction();

                    function isLocationPathDisablingUserState() {

                        var p = $location.path();

                        if(0 === p.indexOf('/completepasswordreset/')) {
                            return true;
                        }

                        return _.contains([
                                '/authenticateuser',
                                '/createuser',
                                '/initiatepasswordreset'
                            ],
                            p);
                    }

                    $scope.canShowBannerActions = function() {
                        return true;
                    };

                    // -----------------
                    // GENERAL

                    $scope.goHideWarnNonProduction = function() {
                        $scope.showWarnNonProduction = false;
                    };

                    // This will take the user back to the home page.

                    $scope.goHome = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome()
                        ]);

                        $rootScope.$broadcast('didResetToHome');

                        return false;
                    };

                    $scope.canGoMore = function() {
                        var p = $location.path();
                        return '/about' !== p;
                    };

                    // This will take the user to a page about the application.

                    $scope.goMore = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createAbout()
                        ]);
                        $scope.showActions = false;
                        return false;
                    };

                    // -----------------
                    // HIDE AND SHOW ACTIONS

                    $scope.goHideActions = function() {
                        $scope.showActions = false;
                    };

                    $scope.goShowActions = function() {
                        $scope.showActions = true;
                    };

                    // -----------------
                    // ROOT ONLY

                    $scope.canShowRootOperations = false;

                    function updateCanShowRootOperations() {
                        $scope.canShowRootOperations = false;
                        var u = userState.user();

                        if(u) {
                            jsonRpc.call(
                                constants.ENDPOINT_API_V1_USER,
                                'getUser',
                                [{ nickname : u.nickname }]
                            ).then(
                                function(result) {
                                    $scope.canShowRootOperations = !!result.isRoot;
                                },
                                function(err) {
                                    errorHandling.handleJsonRpcError(err);
                                }
                            );
                        }
                    }

                    updateCanShowRootOperations();

                    $scope.goRootOperations = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createRootOperations()
                        ]);

                        $scope.showActions = false;
                    };

                    // -----------------
                    // FEEDS

                    $scope.goPkgFeedBuilder = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createPkgFeedBuilder()
                        ]);

                        $scope.showActions = false;
                    };

                    // -----------------
                    // REPOSITORY

                    // note that this is also protected by a permission which is enforced in the template.

                    $scope.canShowRepository = function() {
                        var p = $location.path();
                        return '/repositories' !== p;
                    };

                    $scope.goListRepositories = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createListRepositories()
                        ]);
                        $scope.showActions = false;
                    };

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
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createViewUser(userState.user())
                        ]);
                        $scope.showActions = false;
                    };

                    $scope.goLogout = function() {
                        $scope.showActions = false;
                        userState.token(null);
                        breadcrumbs.resetAndNavigate([breadcrumbFactory.createHome()]);
                    };

                    // -----------------
                    // USER RELATED, BUT NOT CURRENTLY AUTHENTICATED

                    $scope.canAuthenticateOrCreate = function() {
                        return !isLocationPathDisablingUserState() && !userState.user();
                    };

                    $scope.goAuthenticate = function() {
                        breadcrumbs.pushAndNavigate(breadcrumbFactory.createAuthenticate());
                        $scope.showActions = false;
                    };

                    $scope.goCreateUser = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createAddUser()
                        ]);
                        $scope.showActions = false;
                    };

                    // not from a permissions perspective, but from a navigational perspective.
                    $scope.canGoListUsers = function() {
                        var p = $location.path();
                        return '/users' !== p;
                    };

                    $scope.goListUsers = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createListUsers()
                        ]);
                        $scope.showActions = false;
                    };

                    // -----------------
                    // AUTHORIZATION PKG RULES

                    $scope.goListAuthorizationPkgRules = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createListAuthorizationPkgRules()
                        ]);
                        $scope.showActions = false;
                    };

                    // -----------------
                    // REPORTING

                    $scope.canGoReports = function() {
                        return !!userState.user()
                    };

                    $scope.goReports = function() {
                        breadcrumbs.resetAndNavigate([
                            breadcrumbFactory.createHome(),
                            breadcrumbFactory.createReports()
                        ]);
                        $scope.showActions = false;
                    };

                    // -----------------
                    // EVENT HANDLING

                    // when the user logs in or out then the actions may also change; for example, it makes
                    // no sense to show the logout button if nobody is presently logged in.

                    function handleUserChangeSuccess() {
                        var user = userState.user();
                        $scope.userNickname = user ? user.nickname : undefined;
                        updateCanShowRootOperations();
                    }

                    $scope.$on(
                        'userChangeSuccess',
                        function() { handleUserChangeSuccess(); }
                    );

                    // this is done here because the user may be taken from local storage in the user state
                    // service before this banner's lifecycle starts.  Because of this, it would not get
                    // the 'userChangeSuccess' event caused by the initialization of the user state service.

                    handleUserChangeSuccess();

                    // when the natural language changes; maybe because of user choice, default or the user
                    // login | logout, we need to reflect this change in the banner indicator.

                    $scope.$on(
                        'naturalLanguageChange',
                        function(event, newValue, oldValue) {
                            if(!!oldValue) {
                                var nlc;
                                nlc = userState.naturalLanguageCode();

                                if ($scope.naturalLanguageCode !== nlc) {
                                    $scope.naturalLanguageCode = nlc;
                                }
                            }
                        }
                    );

                    // when the user chooses a new natural language to use, this should be configured into the
                    // user state.

                    $scope.$watch(
                        'naturalLanguageCode',
                        function(newValue) {
                            if (newValue && newValue.length) {
                                userState.naturalLanguageCode(newValue);
                            }
                        }
                    );

                }
            ]
    };
});