/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgIconController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgIcon','errorHandling',
        'breadcrumbs',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
            breadcrumbs) {

            var ICON_SIZE_LIMIT = 100 * 1024; // 100k

            $scope.breadcrumbItems = undefined;
            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.editPkgIcon = {
                icon16File : undefined,
                icon32File : undefined
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || $scope.amSaving;
            }

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editPkgIconForm[name].$invalid ? ['form-control-group-error'] : [];
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
                    breadcrumbs.createEditPkgIcon($scope.pkg)
                ];
            }

            // This function will check to make sure that the file is not too large or too small to be a valid PNG.
            // The 'model' is an instance of ngModel from in the form onto which the invalidity can be applied.
            // This will also reset any validation problems that relate to the bad format of the PNG payload from
            // the server because the user will not know if an updated file is also bad until the server has seen it;
            // ie: the validation is happening server-side rather than client-side.

            function validateIconFile(file, model) {
                model.$setValidity('badformatorsize',true);
                model.$setValidity('badsize',undefined==file || (file.size > 24 && file.size < ICON_SIZE_LIMIT));
            }

            function icon32FileDidChange() {
                validateIconFile($scope.editPkgIcon.icon32File, $scope.editPkgIconForm['icon32File']);
            }

            function icon16FileDidChange() {
                validateIconFile($scope.editPkgIcon.icon16File, $scope.editPkgIconForm['icon16File']);
            }

            $scope.$watch('editPkgIcon.icon32File', function(newValue) {
                icon32FileDidChange();
            });

            $scope.$watch('editPkgIcon.icon16File', function(newValue) {
                icon16FileDidChange();
            });

            // This function will take the data from the form and load in the new pkg icons

            $scope.goStorePkgIcons = function() {

                if($scope.editPkgIconForm.$invalid) {
                    throw 'expected the editing of package icons only to be possible if the form is valid';
                }

                $scope.amSaving = true;

                // two PUT requests are made to the server in order to convey the PNG data.

                pkgIcon.setPkgIcon($scope.pkg, $scope.editPkgIcon.icon16File,16).then(
                    function() {
                        $log.info('have set the 16px icon for the pkg '+$scope.pkg.name);

                        pkgIcon.setPkgIcon($scope.pkg, $scope.editPkgIcon.icon32File,32).then(
                            function() {
                                $scope.amSaving = false;
                                $log.info('have set the 32px icon for the pkg '+$scope.pkg.name);
                                $location.path('/viewpkg/'+$routeParams.name+'/'+$routeParams.version+'/'+$routeParams.architectureCode).search({});
                            },
                            function(e) {
                                $scope.amSaving = false;
                                if(e==pkgIcon.errorCodes.BADFORMATORSIZEERROR) {
                                    $scope.editPkgIconForm['icon32File'].$setValidity('badformatorsize',false);
                                }
                                else {
                                    $log.error('unable to set the 32px icon for the pkg '+$scope.pkg.name);
                                    $location.path('/error').search({});
                                }
                            }
                        )
                    },
                    function(e) {
                        $scope.amSaving = false;
                        if(e==pkgIcon.errorCodes.BADFORMATORSIZEERROR) {
                            $scope.editPkgIconForm['icon16File'].$setValidity('badformatorsize',false);
                        }
                        else {
                            $log.error('unable to set the 16px icon for the pkg '+$scope.pkg.name);
                            $location.path('/error').search({});
                        }
                    }
                );
            }

        }
    ]
);