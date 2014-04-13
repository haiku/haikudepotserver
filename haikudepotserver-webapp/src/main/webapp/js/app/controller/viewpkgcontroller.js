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
        function(
            $scope,$log,$location,$routeParams,$rootScope,
            jsonRpc,constants,userState,errorHandling,
            pkgScreenshot,pkgIcon,referenceData,breadcrumbs) {

            var SCREENSHOT_THUMBNAIL_TARGETWIDTH = 480;
            var SCREENSHOT_THUMBNAIL_TARGETHEIGHT = 320;
            var SCREENSHOT_MAX_TARGETHEIGHT = 1500;

            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.pkgScreenshotOffset = 0;
            $scope.pkgIconHvifUrl = undefined;
            $scope.pkgCategories = undefined;

            var hasPkgIcons = undefined;

            refetchPkg();

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg;
            };

            $scope.canRemoveIcon = function() {
                return $scope.pkg && hasPkgIcons;
            };

            $scope.homePageLink = function() {
                var u = undefined;

                if($scope.pkg) {
                    u = _.find(
                        $scope.pkg.versions[0].urls,
                        function(url) {
                            return url.urlTypeCode == 'homepage';
                        });
                }

                return u ? u.url : undefined;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createViewPkg(
                        $scope.pkg,
                        $routeParams.version,
                        $routeParams.architectureCode)
                ]);
            }

            function refetchPkg() {

                $scope.pkg = undefined;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        'getPkg',
                        [{
                            name : $routeParams.name,
                            versionType : 'LATEST',
                            incrementViewCounter : true,
                            architectureCode : $routeParams.architectureCode,
                            naturalLanguageCode: userState.naturalLanguageCode()
                        }]
                    ).then(
                    function(result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                        refetchPkgScreenshots();
                        refetchPkgIconMetaData();
                        refetchPkgCategories();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
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

            // ------------------------
            // SCREENSHOTS

            $scope.goPreviousScreenshot = function() {
                if($scope.pkgScreenshots && $scope.pkgScreenshotOffset > 0) {
                    $scope.pkgScreenshotOffset--;
                }
                return false;
            };

            $scope.goNextScreenshot = function() {
                if($scope.pkgScreenshots && $scope.pkgScreenshotOffset < ($scope.pkgScreenshots.length-1)) {
                    $scope.pkgScreenshotOffset++;
                }
                return false;
            };

            $scope.currentPkgScreenshot = function() {
                return $scope.pkgScreenshots ? $scope.pkgScreenshots[$scope.pkgScreenshotOffset] : null;
            };

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

                        $scope.pkgScreenshotOffset = 0;

                        $log.info('found '+result.items.length+' screenshots for pkg '+$routeParams.name);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // ---------------------
            // ACTIONS FOR PACKAGE

            $scope.goEditIcon = function() {
                $location.path($location.path() + '/editicon').search({});
            };

            $scope.goEditScreenshots = function() {
                $location.path($location.path() + '/editscreenshots').search({});
            };

            $scope.goEditVersionLocalization = function() {
                $location.path($location.path() + '/editversionlocalizations').search({});
            }

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

            }

            // ---------------------
            // EVENTS

            $scope.$on(
                "naturalLanguageChange",
                function() {
                    refetchPkg();
                }
            );

        }
    ]
);