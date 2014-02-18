/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewPkgController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','userState','errorHandling',
        'pkgScreenshot',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,userState,errorHandling,
            pkgScreenshot) {

            var SCREENSHOT_THUMBNAIL_TARGETWIDTH = 480;
            var SCREENSHOT_THUMBNAIL_TARGETHEIGHT = 320;

            $scope.breadcrumbItems = undefined;
            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.pkgScreenshotOffset = 0;

            refetchPkg();

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg;
            }

            $scope.canRemoveIcon = function() {
                return $scope.pkg && $scope.pkg.hasIcon;
            }

            $scope.canEditIcon = function() {
                return $scope.pkg;
            }

            $scope.canEditScreenshots = function() {
                return $scope.pkg;
            }

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
            }

            function refreshBreadcrumbItems() {
                $scope.breadcrumbItems = [{
                    title : $scope.pkg.name,
                    path : $location.path()
                }];
            }

            function refetchPkg() {

                $scope.pkg = undefined;

                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkg",
                        [{
                            name: $routeParams.name,
                            versionType: 'LATEST',
                            architectureCode: $routeParams.architectureCode
                        }]
                    ).then(
                    function(result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                        refetchPkgScreenshots();
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
            }

            $scope.goNextScreenshot = function() {
                if($scope.pkgScreenshots && $scope.pkgScreenshotOffset < ($scope.pkgScreenshots.length-1)) {
                    $scope.pkgScreenshotOffset++;
                }
                return false;
            }

            $scope.currentPkgScreenshot = function() {
                return $scope.pkgScreenshots ? $scope.pkgScreenshots[$scope.pkgScreenshotOffset] : null;
            }

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
                                    item.code)
                            };
                        });

                        $scope.pkgScreenshotOffset = 0;

                        $log.info('found '+result.items.length+' screenshots for pkg '+$routeParams.name);
                        refreshBreadcrumbItems();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // ---------------------
            // ACTIONS FOR PACKAGE

            $scope.goEditIcon = function() {
                $location.path("/editpkgicon/"+$scope.pkg.name).search({'arch':$routeParams.architectureCode});
            }

            $scope.goEditScreenshots = function() {
                $location.path("/editpkgscreenshots/"+$scope.pkg.name).search({'arch':$routeParams.architectureCode});
            }

            $scope.goRemoveIcon = function() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "removePkgIcon",
                        [{ name: $routeParams.name }]
                    ).then(
                    function(result) {
                        $log.info('removed icons for '+$routeParams.name+' pkg');
                        $scope.pkg.hasIcon = false;
                        $scope.pkg.modifyTimestamp = new Date().getTime();
                    },
                    function(err) {
                        $log.error('unable to remove the icons for '+$routeParams.name+' pkg');
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

        }
    ]
);