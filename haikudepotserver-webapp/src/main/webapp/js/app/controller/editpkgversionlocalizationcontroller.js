/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'EditPkgVersionLocalizationController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgIcon','errorHandling',
        'breadcrumbs','userState','referenceData',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
            breadcrumbs,userState,referenceData) {

            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.translations = undefined;
            $scope.selectedTranslation = undefined;
            $scope.editPkgVersionLocalizations = {
            };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || $scope.amSaving || undefined == $scope.translations;
            };

            $scope.isTranslationSelected = function(translation) {
                return $scope.selectedTranslation.naturalLanguage.code == translation.naturalLanguage.code;
            }

            /**
             * <p>This colours the text of the language to indicate if the translation is missing, invalid or
             * has been supplied successfully.</p>
             */

            $scope.classesForTranslation = function(translation) {
                var classes = [];
                var hasSummary = translation.summary && translation.summary.length;
                var hasDescription = translation.description && translation.description.length;

                if(hasDescription && hasSummary) {
                    classes.push('text-success');
                }
                else {
                    if(hasDescription != hasSummary) {
                        classes.push('text-error');
                    }
                    else {
                        classes.push('text-warning');
                    }
                }

                if($scope.isTranslationSelected(translation)) {
                    classes.push('selected-translation');
                }

                return classes;
            }

            $scope.goChooseTranslation = function(translation) {
                if(!translation) {
                    throw 'the translation must be provided to select';
                }

                $scope.selectedTranslation = translation;
            }

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editPkgIconForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createViewPkg(
                        $scope.pkg,
                        $routeParams.version,
                        $routeParams.architectureCode),
                    {
                        titleKey : 'breadcrumb.editPkgVersionLocalizations.title',
                        path : $location.path()
                    }
                ]);
            }

            function setupTranslations() {
                referenceData.naturalLanguages().then(
                    function(data) {
                        $scope.translations = _.map(
                            _.reject(
                                data,
                                function(d) {
                                    return d.code == constants.NATURALLANGUAGECODE_ENGLISH;
                                }
                            ),
                            function(d) {
                                return {
                                    naturalLanguage:d,
                                    summary:'',
                                    description:'',
                                    wasEdited:false
                                };
                            }
                        );

                        $scope.selectedTranslation = $scope.translations[0];

                        $log.info('did setup translations');
                    },
                    function() {
                        errorHandling.navigateToError();
                    }
                );
            }

            // pulls the pkg data back from the server so that it can be used to
            // display the form.

            function refetchPkg() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    'getPkg',
                    [{
                        name: $routeParams.name,
                        versionType : 'LATEST',
                        incrementViewCounter : false,
                        architectureCode : $routeParams.architectureCode,
                        naturalLanguageCode: constants.NATURALLANGUAGECODE_ENGLISH
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
            setupTranslations();

        }
    ]
);