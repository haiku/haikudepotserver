/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewPkgController',
    [
        '$scope','$log','$location','$routeParams','$rootScope',
        'jsonRpc','constants','userState','errorHandling',
        'pkgScreenshot','pkgIcon','referenceData','breadcrumbs',
        'pkg',
        function(
            $scope,$log,$location,$routeParams,$rootScope,
            jsonRpc,constants,userState,errorHandling,
            pkgScreenshot,pkgIcon,referenceData,breadcrumbs,
            pkg) {

            var MAXCHARS_USERRATING_COMMENT = 256;
            var MAXLINES_USERRATING_COMMENT = 4;
            var PAGESIZE_USERRATING = 12;

            var SCREENSHOT_THUMBNAIL_TARGETWIDTH = 320;
            var SCREENSHOT_THUMBNAIL_TARGETHEIGHT = 240;
            var SCREENSHOT_MAX_TARGETHEIGHT = 1500;

            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.pkgIconHvifUrl = undefined;
            $scope.pkgCategories = undefined;
            $scope.userRatings = {
                items : undefined,
                offset : 0,
                max : PAGESIZE_USERRATING,
                total : undefined
            };

            var hasPkgIcons = undefined;

            refetchPkg();

            $scope.isAuthenticated = function() {
                return !!userState.user();
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg;
            };

            $scope.canRemoveIcon = function() {
                return $scope.pkg && hasPkgIcons;
            };

            $scope.canShowDerivedRating = function() {
                return $scope.pkg &&
                    angular.isNumber($scope.pkg.derivedRating) &&
                    $scope.pkg.versions[0].isLatest;
            };

            $scope.homePageLink = function() {
                var u = undefined;

                if($scope.pkg) {
                    //noinspection JSUnresolvedVariable
                    u = _.find(
                        $scope.pkg.versions[0].urls,
                        function(url) {
                            //noinspection JSUnresolvedVariable
                            return url.urlTypeCode == 'homepage';
                        });
                }

                return u ? u.url : undefined;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createViewPkgWithSpecificVersionFromRouteParams($routeParams)
                ]);
            }

            function refetchPkg() {

                $scope.pkg = undefined;

                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, true).then(
                    function(result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                        refetchPkgScreenshots();
                        refetchPkgIconMetaData();
                        refetchPkgCategories();
                        refetchUserRatings();
                    },
                    function() {
                        errorHandling.navigateToError(); // already logged
                    }
                );

            }

            function refetchPkgCategories() {

                referenceData.pkgCategories().then(
                    function(pkgCategories) {
                        $scope.pkgCategories = _.filter(
                            pkgCategories,
                            function(c) { return _.contains($scope.pkg.pkgCategoryCodes, c.code); }
                        );
                    },
                    function() {
                        $log.error('unable to obtain the list of categories');
                        errorHandling.navigateToError();
                    }
                );

            }

            function refetchPkgIconMetaData() {

                $scope.pkgIconHvifUrl = undefined;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkgIcons",
                        [{ pkgName: $routeParams.name }]
                    ).then(
                    function(result) {

                        var has = !!_.findWhere(
                            result.pkgIcons,
                            { mediaTypeCode : constants.MEDIATYPE_HAIKUVECTORICONFILE });

                        $scope.pkgIconHvifUrl = has ? pkgIcon.url(
                            $scope.pkg,
                            constants.MEDIATYPE_HAIKUVECTORICONFILE) : undefined;

                        hasPkgIcons = !!result.pkgIcons.length;
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            /**
             * <p>The user ratings are paginated so this refetch maintains a list of those as well as the
             * offset into the list derived from the database.</p>
             */

            function refetchUserRatings() {

                if($scope.pkg) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_USERRATING,
                        "searchUserRatings",
                        [
                            {
                                offset: $scope.userRatings.offset,
                                limit: $scope.userRatings.max,
                                pkgName: $scope.pkg.name
                            }
                        ]
                    ).then(
                        function (searchUserRatingsData) {

                            // quite a high level of detail comes back, but we actually only want to get a few things
                            // out to display here.

                            $scope.userRatings.items = searchUserRatingsData.items;
                            $scope.userRatings.total = searchUserRatingsData.total;

                            // trim the comments down a bit if necessary.

                            _.each($scope.userRatings.items, function(ur) {
                                 if(ur.comment) {
                                     if(ur.comment.length > MAXCHARS_USERRATING_COMMENT) {
                                         ur.comment = ur.comment.substring(0,MAXCHARS_USERRATING_COMMENT) + '...';
                                     }
                                 }
                            });

                            // trim down the number of lines in the comment if necessary.

                            _.each($scope.userRatings.items, function(ur) {
                                if(ur.comment) {
                                    var lines = ur.comment.split(/\r\n|\n/);

                                    if(lines.length > MAXLINES_USERRATING_COMMENT) {
                                        lines = lines.slice(0,MAXLINES_USERRATING_COMMENT);
                                        lines[lines.length-1] += '...';
                                        ur.comment = lines.join('\n');
                                    }
                                }
                            });

                            // see if the version number differs from that currently being viewed.

                            _.each($scope.userRatings.items, function(ur) {
                                var v0 = $scope.pkg.versions[0];

                                if(ur.pkgVersion.pkg.name != $scope.pkg.name) {
                                    throw 'illegal; a user rating is being shown for another package';
                                }

                                ur.isOtherVersion = ur.pkgVersion.major != v0.major ||
                                    ur.pkgVersion.minor != v0.minor ||
                                    ur.pkgVersion.micro != v0.micro ||
                                    ur.pkgVersion.preRelease != v0.preRelease ||
                                    ur.pkgVersion.revision != v0.revision ||
                                    ur.pkgVersion.architectureCode != v0.architectureCode;
                            });

                        },
                        function (jsonRpcEnvelope) {
                            $log.info('unable to get the user ratings for the package');
                            errorHandling.handleJsonRpcError(jsonRpcEnvelope);
                        }
                    );
                }
            }

            // ------------------------
            // SCREENSHOTS

            function refetchPkgScreenshots() {

                $scope.pkgScreenshots = undefined;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkgScreenshots",
                        [{ pkgName: $scope.pkg.name }]
                    ).then(
                    function(result) {
                        $scope.pkgScreenshots = _.map(result.items, function(item) {
                            return {
                                code : item.code,
                                imageThumbnailUrl : pkgScreenshot.url(
                                    $scope.pkg,
                                    item.code,
                                    SCREENSHOT_THUMBNAIL_TARGETWIDTH,
                                    SCREENSHOT_THUMBNAIL_TARGETHEIGHT),
                                imageDownloadUrl : pkgScreenshot.rawUrl(
                                    $scope.pkg,
                                    item.code),
                                imageLargeUrl : pkgScreenshot.url(
                                    $scope.pkg,
                                    item.code,
                                    SCREENSHOT_MAX_TARGETHEIGHT,
                                    SCREENSHOT_MAX_TARGETHEIGHT)
                            };
                        });

                        $log.info('found '+result.items.length+' screenshots for pkg '+$routeParams.name);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // ---------------------
            // RATINGS

            $scope.goAddUserRating = function() {
                $location.path($location.path() + '/adduserrating').search({});
            };

            $scope.goViewUserRating = function(userRating) {
                $location.path(breadcrumbs.createViewUserRating(userRating).path).search({});
            };

            // ---------------------
            // ACTIONS FOR PACKAGE

            // this is used to cause an authentication in relation to adding a user rating
            $scope.goAuthenticate = function() {
                $location.path('/authenticateuser');
            };

            $scope.goEditIcon = function() {
                $location.path($location.path() + '/editicon').search({});
            };

            $scope.goEditScreenshots = function() {
                $location.path($location.path() + '/editscreenshots').search({});
            };

            $scope.goEditVersionLocalization = function() {
                $location.path($location.path() + '/editversionlocalizations').search({});
            };

            $scope.goEditPkgCategories = function() {
                $location.path($location.path() + '/editcategories').search({});
            };

            $scope.goRemoveIcon = function() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "removePkgIcon",
                        [{ pkgName: $routeParams.name }]
                    ).then(
                    function() {
                        $log.info('removed icons for '+$routeParams.name+' pkg');
                        refetchPkgIconMetaData();
                        $scope.pkg.modifyTimestamp = new Date().getTime();
                    },
                    function(err) {
                        $log.error('unable to remove the icons for '+$routeParams.name+' pkg');
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            };

            // ---------------------
            // EVENTS

            $scope.$on(
                "naturalLanguageChange",
                function() {
                    refetchPkg();
                }
            );

            // the pagination for the user ratings will cause the 'offset' value here to be changed.  This logic will
            // pick this up and will pull an updated page of user ratings back down from the server.

            $scope.$watch('userRatings.offset', function() {
                refetchUserRatings();
            });

        }
    ]
);