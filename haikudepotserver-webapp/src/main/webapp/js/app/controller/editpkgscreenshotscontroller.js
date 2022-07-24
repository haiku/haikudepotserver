/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgScreenshotsController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','pkgScreenshot','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','pkg',
        function(
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,pkgScreenshot,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,pkg) {

            // the upload size must be less than this or it is too big for the
            // far end to process.

            var SCREENSHOT_SIZE_LIMIT = 2 * 1024 * 1024; // 2MB

            var THUMBNAIL_TARGETWIDTH = 180;
            var THUMBNAIL_TARGETHEIGHT = 180;

            $scope.showHelp = false;
            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.amCommunicating = false;
            $scope.addPkgScreenshot = {
                file : undefined
            };

            $scope.shouldSpin = function () {
                return !$scope.pkg || !$scope.pkgScreenshots || $scope.amCommunicating;
            };

            $scope.isSubordinate = function () {
                return $scope.pkg && pkg.isSubordinate($scope.pkg.name);
            };

            $scope.deriveFormControlsContainerClasses = function (name) {
                return $scope.addPkgScreenshotForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // pulls back the list of package screenshots so that they can be displayed
            // in a list.

            function refetchPkgScreenshots() {
                remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_PKG,
                        "get-pkg-screenshots",
                        { pkgName: $routeParams.name }
                    ).then(
                    function (result) {
                        $scope.pkgScreenshots = _.map(result.items, function (item) {
                            return {
                                code : item.code,
                                width : item.width,
                                height : item.height,
                                length : item.length,
                                imageThumbnailUrl : pkgScreenshot.url($scope.pkg,item.code,THUMBNAIL_TARGETWIDTH,THUMBNAIL_TARGETHEIGHT),
                                imageDownloadUrl : pkgScreenshot.rawUrl($scope.pkg,item.code)
                            };
                        });

                        $log.info('found '+result.items.length+' screenshots for pkg '+result.name);
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                    function (result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                        refetchPkgScreenshots();
                    },
                    function () {
                        errorHandling.navigateToError();
                    }
                );
            }

            refetchPkg();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditPkgScreenshots($scope.pkg))
                ]);
            }

            // -------------------------
            // ADDING A SCREENSHOT

            // This function will check to make sure that the file is not too large or too small to be a valid PNG.
            // The 'model' is an instance of ngModel from in the form onto which the invalidity can be applied.
            // This will also reset any validation problems that relate to the bad format of the PNG payload from
            // the server because the user will not know if an updated file is also bad until the server has seen it;
            // ie: the validation is happening server-side rather than client-side.

            $scope.$watch('addPkgScreenshot.file', function() {
                var file = $scope.addPkgScreenshot.file;
                var model = $scope.addPkgScreenshotForm['file'];
                model.$setValidity('badformatorsize',true);
                model.$setValidity('badsize',undefined === file || (file.size > 24 && file.size < SCREENSHOT_SIZE_LIMIT));
            });

            // This function will take the data from the form and will create the user from this data.

            $scope.goAddPkgScreenshot = function() {

                if ($scope.addPkgScreenshotForm.$invalid) {
                    throw Error('expected the editing of package screenshots only to be possible if the form is valid');
                }

                $scope.amCommunicating = true;

                // two PUT requests are made to the server in order to convey the PNG data.

                pkgScreenshot.addScreenshot($scope.pkg, $scope.addPkgScreenshot.file).then(
                    function(code) {
                        $log.info('have added a screenshot for the pkg '+$scope.pkg.name);
                        $scope.addPkgScreenshot.file = undefined;
                        $scope.addPkgScreenshotForm.$setPristine();

                        remoteProcedureCall.call(
                                constants.ENDPOINT_API_V2_PKG,
                                "get-pkg-screenshot",
                                { code : code }
                            ).then(
                            function (result) {
                                $scope.pkgScreenshots.push({
                                    code : code,
                                    height : result.height,
                                    width : result.width,
                                    length : result.length,
                                    imageThumbnailUrl : pkgScreenshot.url($scope.pkg,code,THUMBNAIL_TARGETWIDTH,THUMBNAIL_TARGETHEIGHT),
                                    imageDownloadUrl : pkgScreenshot.rawUrl($scope.pkg,code)
                                });

                                $scope.amCommunicating = false;
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                            }
                        );
                    },
                    function (e) {
                        if (e === pkgScreenshot.errorCodes.BADFORMATORSIZEERROR) {
                            $scope.addPkgScreenshotForm['file'].$setValidity('badformatorsize',false);
                        }
                        else {
                            $log.error('unable to add the screenshot for; '+$scope.pkg.name);
                            errorHandling.navigateToError();
                        }

                        $scope.amCommunicating = false;

                    }
                );
            };

            // -------------------------
            // REMOVE A SCREENSHOT

            $scope.goRemovePkgScreenshot = function(pkgScreenshot) {
                remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_PKG,
                        "remove-pkg-screenshot",
                        { code: pkgScreenshot.code }
                    ).then(
                    function () {
                        $log.info('did remove screenshot '+pkgScreenshot.code);
                        $scope.pkgScreenshots = _.reject($scope.pkgScreenshots, function(item) {
                            return item.code === pkgScreenshot.code;
                        })
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            };

            // -------------------------
            // ORDERING

            function storeOrdering() {

                $scope.amCommunicating = true;

                remoteProcedureCall.call(
                        constants.ENDPOINT_API_V2_PKG,
                        "reorder-pkg-screenshots",
                        {
                            pkgName : $scope.pkg.name,
                            codes: _.map($scope.pkgScreenshots, function(s) { return s.code; })
                        }
                    ).then(
                    function () {
                        $log.info('did re-order screenshots for package '+$scope.pkg.name);
                        $scope.amCommunicating = false;
                    },
                    function (err) {
                        errorHandling.handleRemoteProcedureCallError(err);
                    }
                );
            }

            $scope.goOrderUp = function (pkgScreenshot) {
                var i = _.indexOf($scope.pkgScreenshots, pkgScreenshot);

                switch (i) {

                    case -1:
                        throw Error('unable to find the screenshot to re-order in the list of screenshots');

                    case 0:
                        // already at the start
                        break;

                    default:
                        var earlier = $scope.pkgScreenshots[i-1];
                        $scope.pkgScreenshots[i-1] = pkgScreenshot;
                        $scope.pkgScreenshots[i] = earlier;
                        storeOrdering();
                        break;
                }
            };

            $scope.goOrderDown = function(pkgScreenshot) {
                var i = _.indexOf($scope.pkgScreenshots, pkgScreenshot);

                switch (i) {

                    case -1:
                        throw Error('unable to find the screenshot to re-order in the list of screenshots');

                    case $scope.pkgScreenshots.length-1:
                        // already at the end
                        break;

                    default:
                        var later = $scope.pkgScreenshots[i+1];
                        $scope.pkgScreenshots[i+1] = pkgScreenshot;
                        $scope.pkgScreenshots[i] = later;
                        storeOrdering();
                        break;
                }
            };

            $scope.goHelp = function () {
                $scope.showHelp = true;
            };

        }
    ]
);
