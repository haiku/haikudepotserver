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
        'miscService',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,pkg,
            miscService) {

            var ICON_SIZE_LIMIT = 100 * 1024; // 100k

            $scope.showHelp = false;
            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.editPkgIcon = {
                vectorOrBitmap : 'vector',
                iconBitmap16File : undefined, // bitmap
                iconBitmap32File : undefined, // bitmap
                iconBitmap64File : undefined, // bitmap
                iconHvifFile : undefined // vector 'Haiku Vector Icon Format'
            };

            $scope.shouldSpin = function() {
                return undefined === $scope.pkg || $scope.amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editPkgIconForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                    function (result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
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

            function iconBitmap64FileDidChange() {
                validateBitmapIconFile($scope.editPkgIcon.iconBitmap64File, $scope.editPkgIconForm['iconBitmap64File']);
            }

            function iconBitmap32FileDidChange() {
                validateBitmapIconFile($scope.editPkgIcon.iconBitmap32File, $scope.editPkgIconForm['iconBitmap32File']);
            }

            function iconBitmap16FileDidChange() {
                validateBitmapIconFile($scope.editPkgIcon.iconBitmap16File, $scope.editPkgIconForm['iconBitmap16File']);
            }

            $scope.$watch('editPkgIcon.iconBitmap64File', function () {
                iconBitmap64FileDidChange();
            });

            $scope.$watch('editPkgIcon.iconBitmap32File', function () {
                iconBitmap32FileDidChange();
            });

            $scope.$watch('editPkgIcon.iconBitmap16File', function () {
                iconBitmap16FileDidChange();
            });

            $scope.$watch('editPkgIcon.iconHvifFile', function () {
                validateHvifIconFile($scope.editPkgIcon.iconHvifFile, $scope.editPkgIconForm['iconHvifFile']);
            });

            $scope.isSubordinate = function () {
                return $scope.pkg && pkg.isSubordinate($scope.pkg.name);
            };

            $scope.goHelp = function () {
                $scope.showHelp =true;
            };

            // This function will take the data from the form and load in the new pkg icons

            $scope.goStorePkgIcons = function () {

                if ($scope.editPkgIconForm.$invalid) {
                    throw Error('expected the editing of package icons only to be possible if the form is valid');
                }

                $scope.amSaving = true;

                function handleStorePkgIcons(preparedIconInputs) {

                    jsonRpc.call(
                            constants.ENDPOINT_API_V1_PKG,
                            "configurePkgIcon",
                            [{
                                pkgName: $routeParams.name,
                                pkgIcons: preparedIconInputs
                            }]
                        ).then(
                        function () {
                            $log.info('have updated the pkg icons for pkg '+$scope.pkg.name);
                            breadcrumbs.popAndNavigate();
                            $scope.amSaving = false;
                        },
                        function (err) {

                            switch (err.code) {

                                // the inbound error may involve reporting on bad data.  If this is the case then the error
                                // should be reverse mapped to the input field.

                                case jsonRpc.errorCodes.BADPKGICON:

                                    if (err.data) {
                                        switch (err.data.mediaTypeCode) {

                                            case constants.MEDIATYPE_PNG:
                                                switch(err.data.size) {
                                                    case 16:
                                                        $scope.editPkgIconForm['iconBitmap16File'].$setValidity('badformatorsize',false);
                                                        break;

                                                    case 32:
                                                        $scope.editPkgIconForm['iconBitmap32File'].$setValidity('badformatorsize',false);
                                                        break;

                                                    case 64:
                                                        $scope.editPkgIconForm['iconBitmap64File'].$setValidity('badformatorsize',false);
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

                var iconInputs = [];

                function checkHasCompletedFileReaderProcessing() {

                    _.each(iconInputs, function(iconInput) {
                       if (iconInput.reader && 2 === iconInput.reader.readyState) {
                           iconInput.dataBase64 = miscService.stripBase64FromDataUrl(iconInput.reader.result);
                           iconInput.reader = undefined;
                       }
                    });

                    if (!_.find(iconInputs, function (iconInput) {
                            return iconInput.reader;
                        })) {
                        handleStorePkgIcons(iconInputs);
                    }
                }

                function fileReaderSetup(reader, file) {
                    reader.onloadend = function () { checkHasCompletedFileReaderProcessing(); };
                    reader.readAsDataURL(file);
                    return reader;
                }

                function bitmapIconInputSetup(size, file) {
                    return {
                        reader : fileReaderSetup(new FileReader(), file),
                        mediaTypeCode : constants.MEDIATYPE_PNG,
                        size: size
                    };
                }

                // pull in all of the data as base64-ized data URLs

                switch($scope.editPkgIcon.vectorOrBitmap) {

                    case 'vector':
                        iconInputs.push({
                            reader : fileReaderSetup(new FileReader(), $scope.editPkgIcon.iconHvifFile),
                            mediaTypeCode : constants.MEDIATYPE_HAIKUVECTORICONFILE
                        });
                        break;

                    case 'bitmap':
                        iconInputs.push(bitmapIconInputSetup(16,$scope.editPkgIcon.iconBitmap16File));
                        iconInputs.push(bitmapIconInputSetup(32,$scope.editPkgIcon.iconBitmap32File));
                        iconInputs.push(bitmapIconInputSetup(64,$scope.editPkgIcon.iconBitmap64File));
                        break;

                    default:
                        throw Error('unknown vectorOrBitmap value');
                }

            }; // goStorePkgIcons

        }
    ]
);
