/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgIconController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','pkg',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,pkg) {

            var ICON_SIZE_LIMIT = 100 * 1024; // 100k

            $scope.showHelp = false;
            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.editPkgIcon = {
                iconBitmap16File : undefined, // bitmap
                iconBitmap32File : undefined, // bitmap
                iconHvifFile : undefined // vector 'Haiku Vector Icon Format'
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || $scope.amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editPkgIconForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                    function(result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                    },
                    function() {
                        errorHandling.navigateToError();
                    }
                );
            }

            refetchPkg();

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditPkgIcon($scope.pkg))
                ]);
            }

            // This function will check to make sure that the file is not too large or too small to be a valid PNG.
            // The 'model' is an instance of ngModel from in the form onto which the invalidity can be applied.
            // This will also reset any validation problems that relate to the bad format of the PNG payload from
            // the server because the user will not know if an updated file is also bad until the server has seen it;
            // ie: the validation is happening server-side rather than client-side.

            function validateBitmapIconFile(file, model) {
                model.$setValidity('badformatorsize',true);
                model.$setValidity('badsize',undefined==file || (file.size > 24 && file.size < ICON_SIZE_LIMIT));
            }

            function validateHvifIconFile(file, model) {
                model.$setValidity('badformatorsize',true);
                model.$setValidity('badsize',undefined==file || (file.size > 4 && file.size < ICON_SIZE_LIMIT));
            }

            function iconBitmap32FileDidChange() {
                validateBitmapIconFile($scope.editPkgIcon.iconBitmap32File, $scope.editPkgIconForm['iconBitmap32File']);
            }

            function iconBitmap16FileDidChange() {
                validateBitmapIconFile($scope.editPkgIcon.iconBitmap16File, $scope.editPkgIconForm['iconBitmap16File']);
            }

            $scope.$watch('editPkgIcon.iconBitmap32File', function() {
                iconBitmap32FileDidChange();
            });

            $scope.$watch('editPkgIcon.iconBitmap16File', function() {
                iconBitmap16FileDidChange();
            });

            $scope.$watch('editPkgIcon.iconHvifFile', function() {
                validateHvifIconFile($scope.editPkgIcon.iconHvifFile, $scope.editPkgIconForm['iconHvifFile']);
            });

            $scope.goHelp = function() {
                $scope.showHelp =true;
            };

            $scope.goClearIconHvifFile = function() {
                $scope.editPkgIcon.iconHvifFile = undefined;
            };

            // This function will take the data from the form and load in the new pkg icons

            $scope.goStorePkgIcons = function() {

                if($scope.editPkgIconForm.$invalid) {
                    throw Error('expected the editing of package icons only to be possible if the form is valid');
                }

                $scope.amSaving = true;

                function handleStorePkgIcons(base64IconBitmap16, base64IconBitmap32, base64IconHvif) {

                    var pkgIcons = [
                        {
                            mediaTypeCode : constants.MEDIATYPE_PNG,
                            size : 16,
                            dataBase64 : base64IconBitmap16
                        },
                        {
                            mediaTypeCode : constants.MEDIATYPE_PNG,
                            size : 32,
                            dataBase64 : base64IconBitmap32
                        }
                    ];

                    if(base64IconHvif) {
                        pkgIcons.push({
                            mediaTypeCode : constants.MEDIATYPE_HAIKUVECTORICONFILE,
                            dataBase64 : base64IconHvif
                        });
                    }

                    jsonRpc.call(
                            constants.ENDPOINT_API_V1_PKG,
                            "configurePkgIcon",
                            [{
                                pkgName: $routeParams.name,
                                pkgIcons: pkgIcons
                            }]
                        ).then(
                        function() {
                            $log.info('have updated the pkg icons for pkg '+$scope.pkg.name);
                            breadcrumbs.popAndNavigate();
                            $scope.amSaving = false;
                        },
                        function(err) {

                            switch(err.code) {

                                // the inbound error may involve reporting on bad data.  If this is the case then the error
                                // should be reverse mapped to the input field.

                                case jsonRpc.errorCodes.BADPKGICON:

                                    if(err.data) {
                                        switch(err.data.mediaTypeCode) {

                                            case constants.MEDIATYPE_PNG:
                                                switch(err.data.size) {
                                                    case 16:
                                                        $scope.editPkgIconForm['iconBitmap16File'].$setValidity('badformatorsize',false);
                                                        break;

                                                    case 32:
                                                        $scope.editPkgIconForm['iconBitmap32File'].$setValidity('badformatorsize',false);
                                                        break;

                                                    default:
                                                        throw Error('expected size; ' + error.data.size);
                                                }
                                                break;

                                            case constants.MEDIATYPE_HAIKUVECTORICONFILE:
                                                $scope.editPkgIconForm['iconHvifFile'].$setValidity('badformatorsize',false);
                                                break;

                                            default:
                                                throw Error('unexpected media type code; ' + err.data.mediaTypeCode);

                                        }
                                    }
                                    else {
                                        throw Error('expected data to be supplied with a bad pkg icon');
                                    }

                                    break;

                                default:
                                    errorHandling.handleJsonRpcError(err);
                                    break;

                            }

                            $scope.amSaving = false;

                        }
                    );

                }

                // pull in all of the data as base64-ized data URLs

                var readerIconBitmap16 = new FileReader();
                var readerIconBitmap32 = new FileReader();
                var readerHvif = $scope.editPkgIcon.iconHvifFile ? new FileReader() : undefined;

                function checkHasCompletedFileReaderProcessing() {

                    // data urls can come in a number of forms.  This function will strip ut the data material and
                    // just get at the base64.  If the data is not base64, it will throw an exception.  Maybe a more
                    // elaborate handling will be required?

                    function dataUrlToBase64(u) {

                        if(!u) {
                            throw Error('the data url must be supplied to convert to base64');
                        }

                        if(0!= u.indexOf('data:')) {
                            throw Error('the data url was unable to be converted to base64 because it does not look like a data url');
                        }

                        var commaI = u.indexOf(',');

                        if(-1==commaI) {
                            throw Error('expecting comma in data url to preceed the base64 data');
                        }

                        if(!_.indexOf(u.substring(5,commaI).split(';'),'base64')) {
                            throw Error('expecting base64 to appear in the data url');
                        }

                        return u.substring(commaI+1);
                    }

                   if(2==readerIconBitmap16.readyState
                       && 2==readerIconBitmap32.readyState
                       && (!readerHvif || 2==readerHvif.readyState)) {

                       handleStorePkgIcons(
                           dataUrlToBase64(readerIconBitmap16.result),
                           dataUrlToBase64(readerIconBitmap32.result),
                           readerHvif ? dataUrlToBase64(readerHvif.result) : null);
                   }
                }

                readerIconBitmap16.onloadend = function() {
                    checkHasCompletedFileReaderProcessing();
                };

                readerIconBitmap16.readAsDataURL($scope.editPkgIcon.iconBitmap16File);

                readerIconBitmap32.onloadend = function() {
                    checkHasCompletedFileReaderProcessing();
                };

                readerIconBitmap32.readAsDataURL($scope.editPkgIcon.iconBitmap32File);

                if($scope.editPkgIcon.iconHvifFile) {

                    readerHvif.onloadend = function() {
                        checkHasCompletedFileReaderProcessing();
                    };

                    readerHvif.readAsDataURL($scope.editPkgIcon.iconHvifFile);
                }

            }; // goStorePkgIcons

        }
    ]
);
