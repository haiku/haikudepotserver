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

            var ARCHITECTUREAPPLICABILITY_ALL = '__all';

            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.translations = undefined;
            $scope.architectureCode = $routeParams.architectureCode;
            $scope.selectedArchitectureApplicability = ARCHITECTUREAPPLICABILITY_ALL;
            $scope.originalTranslations = undefined;
            $scope.selectedTranslation = undefined;
            $scope.editPkgVersionLocalizations = { };

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || $scope.amSaving || undefined == $scope.translations;
            };

            $scope.isTranslationSelected = function(translation) {
                return $scope.selectedTranslation.naturalLanguage.code == translation.naturalLanguage.code;
            };

            function findOriginalTranslation(naturalLanguageCode) {
                var result = _.find(
                    $scope.originalTranslations,
                    function(t) {
                        return t.naturalLanguage.code == naturalLanguageCode;
                    }
                );

                if(!result) {
                    throw 'was not able to find the original translation';
                }

                return result;
            }

            /**
             * <p>The only requirement for a translation to be valid is that if the description and the summary
             * are either present or not present.  It is not possible to have a summary and not a description
             * and vica-versa.</p>
             */

            $scope.isTranslationValid = function(translation) {
                if(translation) {
                    var hasSummary = translation.summary && translation.summary.length;
                    var hasDescription = translation.description && translation.description.length;
                    return !!hasDescription == !!hasSummary;
                }

                return true;
            };

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
            };

            $scope.goChooseTranslation = function(translation) {
                if(!translation) {
                    throw 'the translation must be provided to select';
                }

                $scope.selectedTranslation = translation;
            };

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
                    function(naturalLanguageData) {

                        // now we need to get the _existing_ translations for the package.

                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_PKG,
                            'getPkgVersionLocalizations',
                            [{
                                pkgName: $routeParams.name,
                                naturalLanguageCodes : _.map(
                                    naturalLanguageData,
                                    function(d) {
                                        return d.code;
                                    }
                                ),
                                architectureCode : $routeParams.architectureCode
                            }]
                        )
                        .then(
                            function(pkgVersionLocalizationsData) {

                                // now merge the data about the various natural languages together with the data about
                                // the packages existing localizations and we should have enough working data to setup
                                // an internal data model.

                                $scope.translations = _.map(

                                    // don't include English as a localization target because the English language will
                                    // have been included in the hpkg data from the repository.

                                    _.reject(
                                        naturalLanguageData,
                                        function(d) {
                                            return d.code == constants.NATURALLANGUAGECODE_ENGLISH;
                                        }
                                    ),
                                    function(d) {

                                        var pkgVersionLocalizationData = _.findWhere(
                                            pkgVersionLocalizationsData.pkgVersionLocalizations,
                                            { naturalLanguageCode : d.code }
                                        );

                                        return {
                                            naturalLanguage:d,
                                            summary: pkgVersionLocalizationData ? pkgVersionLocalizationData.summary : '',
                                            description: pkgVersionLocalizationData ? pkgVersionLocalizationData.description : '',
                                            wasEdited:false
                                        };
                                    }
                                );

                                $scope.originalTranslations = angular.copy($scope.translations);
                                $scope.selectedTranslation = $scope.translations[0];

                                $log.info('did setup translations');

                            },
                            function(jsonRpcEnvelope) {
                                errorHandling.handleJsonRpcError(jsonRpcEnvelope);
                            }
                        );
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

            // --------------------------
            // SAVE CHANGES

            /**
             * <p>It is possible to save the translations if something has been edited and if there are no validity
             * problems with the translations.</p>
             */

            $scope.canSave = function() {
                return !!_.findWhere(
                    $scope.translations,
                    { wasEdited : true }
                ) &&
                    !_.find(
                        $scope.translations,
                        function(t) {
                            return !$scope.isTranslationValid(t);
                        }
                    );
            };

            /**
             * <p>This method will persist those changes to the translations back into the server.</p>
             */

            $scope.saveEditedLocalizations = function() {

                 if(!$scope.canSave()) {
                     throw 'not possible to save edited localizations';
                 }

                $scope.amSaving = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_PKG,
                    'updatePkgVersionLocalization',
                    [{
                        pkgName: $routeParams.name,
                        architectureCode: $routeParams.architectureCode,
                        replicateToOtherArchitecturesWithSameEnglishContent: $scope.selectedArchitectureApplicability == ARCHITECTUREAPPLICABILITY_ALL,
                        pkgVersionLocalizations: _.map(
                            _.filter(
                                $scope.translations,
                                function(t) { return t.wasEdited; }
                            ),
                            function(t) {
                                return {
                                    naturalLanguageCode : t.naturalLanguage.code,
                                    summary : t.summary,
                                    description : t.description
                                };
                            }
                        )
                    }]
                ).then(
                    function() {
                        $log.info('updated localization on '+$routeParams.name+' pkg');
                        breadcrumbs.popAndNavigate();
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

            // --------------------------
            // INIT

            refetchPkg();
            setupTranslations();

            // --------------------------
            // EVENT HANDLING

            // this watch will keep an eye on the summary and description.  If they have changed in isolation (ie;
            // the selected translation has not changed, then we can mark the translation as edited.

            $scope.$watch(
                'selectedTranslation',
                function(newValue, oldValue) {
                    if(null!=oldValue) {
                        if(oldValue.naturalLanguage.code == newValue.naturalLanguage.code &&
                            (oldValue.summary != newValue.summary || oldValue.description != newValue.description) ) {

                            // quick check to see if the new values equal the original values.

                            var originalTranslation = findOriginalTranslation(newValue.naturalLanguage.code);
                            $scope.selectedTranslation.wasEdited = (originalTranslation.summary != newValue.summary) ||
                                (originalTranslation.description != newValue.description);
                        }
                    }
                },
                true);

        }
    ]
);