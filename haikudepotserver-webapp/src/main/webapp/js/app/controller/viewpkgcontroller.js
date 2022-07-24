/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewPkgController',
    [
        '$scope','$log','$location','$routeParams','$rootScope','$timeout',
        'remoteProcedureCall','constants','userState','errorHandling',
        'pkgScreenshot','pkgIcon','referenceData','breadcrumbs',
        'pkg','breadcrumbFactory','repositoryService',
        function(
            $scope,$log,$location,$routeParams,$rootScope,$timeout,
            remoteProcedureCall,constants,userState,errorHandling,
            pkgScreenshot,pkgIcon,referenceData,breadcrumbs,
            pkg,breadcrumbFactory,repositoryService) {

            var MAXCHARS_USERRATING_COMMENT = 256;
            var MAXLINES_USERRATING_COMMENT = 4;
            var PAGESIZE_USERRATING = 12;

            var SCREENSHOT_MAX_TARGETHEIGHT = 1500;
            var SCREENSHOT_THUMBNAIL_TARGETWIDTH = 320;
            var SCREENSHOT_THUMBNAIL_TARGETHEIGHT = 240;

            $scope.didDeriveAndStoreUserRating = false;
            $scope.didDeriveAndStoreUserRatingTimeout = undefined;

            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.pkgIconHvifUrl = undefined;
            $scope.pkgHpkgUrl = undefined;
            $scope.pkgCategories = undefined;
            $scope.userRatings = {
                items : undefined,
                offset : 0,
                max : PAGESIZE_USERRATING,
                total : undefined
            };

            var hasPkgIcons = undefined;

            refetchPkg();

            $scope.isAuthenticated = function () {
                return !!userState.user();
            };

            $scope.shouldSpin = function () {
                return undefined === $scope.pkg;
            };

            $scope.canRemoveIcon = function () {
                return $scope.pkg && hasPkgIcons;
            };

            $scope.canShowDerivedRating = function () {
                return $scope.pkg &&
                    angular.isNumber($scope.pkg.derivedRating) &&
                    $scope.pkg.versions[0].isLatest;
            };

            $scope.homePageLink = function () {
                var u = undefined;

                if ($scope.pkg) {
                    //noinspection JSUnresolvedVariable
                    u = _.find(
                        $scope.pkg.versions[0].urls,
                        function (url) {
                            //noinspection JSUnresolvedVariable
                            return url.urlTypeCode === 'homepage';
                        });
                }

                return u ? u.url : undefined;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg))
                ]);
            }

            function refreshHpkgUrl() {

                function nullToHyphen(s) {
                    if (!s) {
                        return '-';
                    }

                    return '' + s;
                }

                $scope.pkgHpkgUrl = [
                    '/__pkgdownload',
                    $scope.pkg.name,
                    $scope.pkg.versions[0].repositoryCode,
                    nullToHyphen($scope.pkg.versions[0].major),
                    nullToHyphen($scope.pkg.versions[0].minor),
                    nullToHyphen($scope.pkg.versions[0].micro),
                    nullToHyphen($scope.pkg.versions[0].preRelease),
                    nullToHyphen($scope.pkg.versions[0].revision),
                    $scope.pkg.versions[0].architectureCode,
                    'package.hpkg'
                ].join('/');
            }

            function refetchPkg() {

                $scope.pkg = undefined;

                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, true).then(
                    function (result) {

                        $scope.pkg = result;

                        $log.info('found '+result.name+' pkg');

                        refreshBreadcrumbItems();
                        refreshHpkgUrl();
                        refetchRepository();
                        refetchPkgScreenshots();
                        refetchPkgIconMetaData();
                        refetchPkgCategories();
                        refetchUserRatings();
                        refetchProminence();

                    },
                    function () {
                        errorHandling.navigateToError(); // already logged
                    }
                );

            }

            function refetchRepository() {
                repositoryService.getRepositories().then(
                    function(data) {
                        var repository = _.findWhere(
                            data,
                            { code : $scope.pkg.versions[0].repositoryCode }
                        );

                        if (!repository) {
                            throw Error('unknown repository; ' + $scope.pkg.versions[0].repositoryCode);
                        }

                        $scope.pkg.versions[0].repository = repository;
                        $scope.pkg.versions[0].repositorySource = {
                            code : $scope.pkg.versions[0].repositorySourceCode,
                            repository : repository
                        };

                    },
                    function () {
                        throw Error('unable to get all of the repositories');
                    }
                );
            }

            function refetchProminence() {
                referenceData.prominences().then(
                    function (prominences) {
                        $scope.pkg.prominence = _.findWhere(
                            prominences,
                            { ordering : $scope.pkg.prominenceOrdering }
                        );
                    },
                    function () {
                        $log.error('unable to obtain the list of prominences');
                        errorHandling.navigateToError();
                    }
                )
            }

            function refetchPkgCategories() {

                referenceData.pkgCategories().then(
                    function (pkgCategories) {
                        $scope.pkgCategories = _.filter(
                            pkgCategories,
                            function(c) { return _.contains($scope.pkg.pkgCategoryCodes, c.code); }
                        );
                    },
                    function () {
                        $log.error('unable to obtain the list of categories');
                        errorHandling.navigateToError();
                    }
                );

            }

            function refetchPkgIconMetaData() {

                $scope.pkgIconHvifUrl = undefined;

                var pkgName = $routeParams.name;

                if (!pkgName || !pkgName.length) {
                    throw new Error('illegal state -- the package name should be available; ' + $location.path());
                }

              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    "get-pkg-icons",
                    { pkgName: pkgName }
                ).then(
                    function (result) {

                        var has = !!_.findWhere(
                            result.pkgIcons,
                            { mediaTypeCode : constants.MEDIATYPE_HAIKUVECTORICONFILE });

                        $scope.pkgIconHvifUrl = has ? pkgIcon.url(
                            $scope.pkg,
                            constants.MEDIATYPE_HAIKUVECTORICONFILE) : undefined;

                        hasPkgIcons = !!result.pkgIcons.length;
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );

            }

            /**
             * <p>The user ratings are paginated so this refetch maintains a list of those as well as the
             * offset into the list derived from the database.</p>
             */

            function refetchUserRatings() {

                if ($scope.pkg) {
                  remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_USERRATING,
                        "search-user-ratings",
                        {
                            offset: $scope.userRatings.offset,
                            limit: $scope.userRatings.max,
                            repositoryCode: $scope.pkg.versions[0].repositoryCode,
                            pkgName: $scope.pkg.name
                        }
                    ).then(
                        function (searchUserRatingsData) {

                            // quite a high level of detail comes back, but we actually only want to get a few things
                            // out to display here.

                            $scope.userRatings.items = searchUserRatingsData.items;
                            $scope.userRatings.total = searchUserRatingsData.total;

                            // trim the comments down a bit if necessary.

                            _.each($scope.userRatings.items, function (ur) {
                                if (ur.comment) {
                                    if (ur.comment.length > MAXCHARS_USERRATING_COMMENT) {
                                        ur.comment = ur.comment.substring(0,MAXCHARS_USERRATING_COMMENT) + '...';
                                    }
                                }
                            });

                            // trim down the number of lines in the comment if necessary.

                            _.each($scope.userRatings.items, function(ur) {
                                if (ur.comment) {
                                    var lines = ur.comment.split(/\r\n|\n/);

                                    if (lines.length > MAXLINES_USERRATING_COMMENT) {
                                        lines = lines.slice(0,MAXLINES_USERRATING_COMMENT);
                                        lines[lines.length-1] += '...';
                                        ur.comment = lines.join('\n');
                                    }
                                }
                            });

                            // see if the version number differs from that currently being viewed.

                            _.each($scope.userRatings.items, function(ur) {
                                var v0 = $scope.pkg.versions[0];

                                if (ur.pkgVersion.pkg.name !== $scope.pkg.name) {
                                    throw Error('illegal; a user rating is being shown for another package');
                                }

                                ur.isOtherVersion = ur.pkgVersion.major !== v0.major ||
                                    ur.pkgVersion.minor !== v0.minor ||
                                    ur.pkgVersion.micro !== v0.micro ||
                                    ur.pkgVersion.preRelease !== v0.preRelease ||
                                    ur.pkgVersion.revision !== v0.revision ||
                                    ur.pkgVersion.architectureCode !== v0.architectureCode;
                            });

                        },
                        function (remoteProcedureCallEnvelope) {
                            $log.info('unable to get the user ratings for the package');
                            errorHandling.handleRemoteProcedureCallError(remoteProcedureCallEnvelope);
                        }
                    );
                }
            }

            // ------------------------
            // SCREENSHOTS

            function refetchPkgScreenshots() {

                $scope.pkgScreenshots = undefined;

              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    "get-pkg-screenshots",
                    { pkgName: $scope.pkg.name }
                ).then(
                    function (result) {

                        var factor = window.devicePixelRatio ? window.devicePixelRatio : 1.0;

                        $scope.pkgScreenshots = _.map(result.items, function(item) {

                            var scale = Math.min(
                                SCREENSHOT_THUMBNAIL_TARGETWIDTH / item.width,
                                SCREENSHOT_THUMBNAIL_TARGETHEIGHT / item.height);

                            var adjustedThumbnailWidth = Math.floor(scale * item.width);
                            var adjustedThumbnailHeight = Math.floor(scale * item.height);
                            var adjustedThumbnailBitmapWidth = Math.floor(adjustedThumbnailWidth * factor);
                            var adjustedThumbnailBitmapHeight = Math.floor(adjustedThumbnailHeight * factor);

                            return {
                                code : item.code,
                                thumbnailWidth: adjustedThumbnailWidth,
                                thumbnailHeight: adjustedThumbnailHeight,
                                imageThumbnailUrl : pkgScreenshot.url(
                                    $scope.pkg,
                                    item.code,
                                    adjustedThumbnailBitmapWidth,
                                    adjustedThumbnailBitmapHeight),
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
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );

            }

            // ---------------------
            // ACTIONS FOR PACKAGE

            $scope.goPkgFeedBuilder = function () {
                var item = breadcrumbFactory.createPkgFeedBuilder();
                breadcrumbFactory.applySearch(item, { pkgNames : $scope.pkg.name });
                breadcrumbs.pushAndNavigate(item);
            };

            // this is used to cause an authentication in relation to adding a user rating
            $scope.goAuthenticate = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createAuthenticate());
            };

            // This is a bit strange; we're cycling through a list of package versions, but
            // need to make the argument to the function look as if we are only looking at
            // one version.

            $scope.goViewLocalization = function () {
                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewPkgVersionLocalization($scope.pkg));
            };

            /**
             * <p>Sends a request off to enqueue that a package should have its derived rating re-calculated
             * and stored.  It will display a little message to indicate that this has happened and the
             * little message will vanish after a few moments.</p>
             */

            $scope.goDeriveAndStoreUserRating = function () {
              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_USERRATING,
                    "derive-and-store-user-rating-for-pkg",
                    { pkgName: $routeParams.name }
                ).then(
                    function () {
                        $log.info('requested derive and store user rating for '+$routeParams.name+' pkg');
                        $scope.didDeriveAndStoreUserRating = true;

                        if ($scope.didDeriveAndStoreUserRatingTimeout) {
                            $timeout.cancel($scope.didDeriveAndStoreUserRatingTimeout);
                        }

                        $scope.didDeriveAndStoreUserRatingTimeout = $timeout(function() {
                            $scope.didDeriveAndStoreUserRating = false;
                            $scope.didDeriveAndStoreUserRatingTimeout = undefined;
                        }, 3000);
                    },
                    function(err) {
                        $log.error('unable to derive and store user rating for '+$routeParams.name+' pkg');
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            $scope.goRemoveIcon = function() {
              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    "remove-pkg-icon",
                    { pkgName: $routeParams.name }
                ).then(
                    function () {
                        $log.info('removed icons for '+$routeParams.name+' pkg');
                        refetchPkgIconMetaData();
                        $scope.pkg.modifyTimestamp = new Date().getTime();
                    },
                    function (err) {
                        $log.error('unable to remove the icons for '+$routeParams.name+' pkg');
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );

            };

            $scope.goDeactivate = function () {
                var pv = $scope.pkg.versions[0];

              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    "update-pkg-version",
                    {
                        filter : [ 'ACTIVE' ],
                        active : false,
                        repositorySourceCode : pv.repositorySource.code,
                        pkgName : $routeParams.name,
                        architectureCode : pv.architectureCode,
                        major : pv.major,
                        minor : pv.minor,
                        micro : pv.micro,
                        preRelease : pv.preRelease,
                        revision : pv.revision
                    }
                ).then(
                    function () {
                        $log.info('deactivated '+$routeParams.name+' pkg version');
                        refetchPkgIconMetaData();
                        $scope.pkg.modifyTimestamp = new Date().getTime();
                        $scope.pkg.versions[0].active = false;
                    },
                    function (err) {
                        $log.error('unable to deactivate '+$routeParams.name);
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            /**
             * <p>This function will produce a spreadsheet of the user ratings for this
             * package.</p>
             */

            $scope.goDownloadUserRatings = function () {
              remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_USERRATING_JOB,
                    'queue-user-rating-spreadsheet-job',
                    {
                        pkgName: $routeParams.name,
                        repositoryCode : $scope.pkg.versions[0].repositoryCode
                    }
                ).then(
                    function (data) {
                        if (data.guid && data.guid.length) {
                            breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewJob({ guid:data.guid }));
                        }
                        else {
                            $log.warn('attempt to run the user rating spreadsheet job failed');
                            // TODO; some sort of user-facing indication of this?
                        }
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            // ---------------------
            // EVENTS

            $scope.$on(
                "naturalLanguageChange",
                function (event, newValue, oldValue) {
                    if (!!oldValue) {
                        refetchPkg();
                    }
                }
            );

            // the pagination for the user ratings will cause the 'offset' value here to be changed.  This logic will
            // pick this up and will pull an updated page of user ratings back down from the server.

            $scope.$watch('userRatings.offset', function () {
                refetchUserRatings();
            });

        }
    ]
);
