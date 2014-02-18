/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgScreenshotsController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgScreenshot','errorHandling',
        'breadcrumbs',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgScreenshot,errorHandling,
            breadcrumbs) {

            // the upload size must be less than this or it is too big for the
            // far end to process.

            var FILESIZEMAX = 2 * 1024 * 1024;

            var THUMBNAIL_TARGETWIDTH = 180;
            var THUMBNAIL_TARGETHEIGHT = 90;

            $scope.breadcrumbItems = undefined;
            $scope.pkg = undefined;
            $scope.pkgScreenshots = undefined;
            $scope.amCommunicating = false;
            $scope.addPkgScreenshot = {
                file : undefined
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || undefined == $scope.pkgScreenshots || $scope.amCommunicating;
            }

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addPkgScreenshotForm[name].$invalid ? ['form-control-group-error'] : [];
            }

            // pulls back the list of package screenshots so that they can be displayed
            // in a list.

            function refetchPkgScreenshots() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkgScreenshots",
                        [{ pkgName: $routeParams.name }]
                    ).then(
                    function(result) {
                        $scope.pkgScreenshots = _.map(result.items, function(item) {
                            return {
                                code : item.code,
                                imageThumbnailUrl : pkgScreenshot.url($scope.pkg,item.code,THUMBNAIL_TARGETWIDTH,THUMBNAIL_TARGETHEIGHT),
                                imageDownloadUrl : pkgScreenshot.rawUrl($scope.pkg,item.code)
                            };
                        })

                        $log.info('found '+result.items.length+' screenshots for pkg '+result.name);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "getPkg",
                        [{
                            name: $routeParams.name,
                            versionType: 'NONE',
                            architectureCode: undefined // not required if we don't need the version
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

            refetchPkg();

            function refreshBreadcrumbItems() {
                $scope.breadcrumbItems = [
                    breadcrumbs.createViewPkg(
                        $scope.pkg,
                        $routeParams.version,
                        $routeParams.architectureCode),
                    breadcrumbs.createEditPkgScreenshots($scope.pkg)
                ];
            }

            // -------------------------
            // ADDING A SCREENSHOT

            // This function will check to make sure that the file is not too large or too small to be a valid PNG.
            // The 'model' is an instance of ngModel from in the form onto which the invalidity can be applied.
            // This will also reset any validation problems that relate to the bad format of the PNG payload from
            // the server because the user will not know if an updated file is also bad until the server has seen it;
            // ie: the validation is happening server-side rather than client-side.

            $scope.$watch('addPkgScreenshot.file', function(newValue) {
                var file = $scope.addPkgScreenshot.file;
                var model = $scope.addPkgScreenshotForm['file']
                model.$setValidity('badformatorsize',true);
                model.$setValidity('badsize',undefined==file || (file.size > 24 && file.size < FILESIZEMAX));
            });

            // This function will take the data from the form and will create the user from this data.

            $scope.goAddPkgScreenshot = function() {

                if($scope.addPkgScreenshotForm.$invalid) {
                    throw 'expected the editing of package screenshots only to be possible if the form is valid';
                }

                $scope.amCommunicating = true;

                // two PUT requests are made to the server in order to convey the PNG data.

                pkgScreenshot.addScreenshot($scope.pkg, $scope.addPkgScreenshot.file).then(
                    function(code) {
                        $log.info('have added a screenshot for the pkg '+$scope.pkg.name);
                        $scope.addPkgScreenshot.file = undefined;
                        $scope.addPkgScreenshotForm.$setPristine();
                        $scope.pkgScreenshots.push({
                            code : code,
                            imageThumbnailUrl : pkgScreenshot.url($scope.pkg,code,THUMBNAIL_TARGETWIDTH,THUMBNAIL_TARGETHEIGHT),
                            imageDownloadUrl : pkgScreenshot.rawUrl($scope.pkg,code)
                        })
                    },
                    function(e) {
                        if(e==pkgScreenshot.errorCodes.BADFORMATORSIZEERROR) {
                            $scope.addPkgScreenshotForm['file'].$setValidity('badformatorsize',false);
                        }
                        else {
                            $log.error('unable to add the screenshot for; '+$scope.pkg.name);
                            $location.path('/error').search({});
                        }
                    }
                )['finally'](
                    function() {
                        $scope.amCommunicating = false;

                    }
                )
            }

            // -------------------------
            // REMOVE A SCREENSHOT

            $scope.goRemovePkgScreenshot = function(pkgScreenshot) {
                jsonRpc.call(
                        constants.ENDPOINT_API_V1_PKG,
                        "removePkgScreenshot",
                        [{ code: pkgScreenshot.code }]
                    ).then(
                    function() {
                        $log.info('did remove screenshot '+pkgScreenshot.code);
                        $scope.pkgScreenshots = _.reject($scope.pkgScreenshots, function(item) {
                            return item.code == pkgScreenshot.code;
                        })
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

        }
    ]
);