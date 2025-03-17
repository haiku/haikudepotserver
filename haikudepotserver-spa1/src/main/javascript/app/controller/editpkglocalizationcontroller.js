/*
 * Copyright 2015-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller allows the user to be able to provide translations for the package.
 * The scope contains a number of 'translations' that couple the natural language together with
 * the data that has been translated.  Only translations for some languages will be shown; those
 * with existing data to start with.</p>
 *
 * <p>Note that this controller manages the translations on the package rather than the package
 * version.</p>
 */

angular.module('haikudepotserver').controller(
    'EditPkgLocalizationController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','referenceData','pkg','messageSource',
        function (
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,referenceData,pkg,messageSource) {

            $scope.showHelp = false;
            $scope.pkg = undefined;
            $scope.amSaving = false;
            $scope.translations = undefined;
            $scope.originalTranslations = undefined;
            $scope.selectedTranslation = undefined;
            $scope.addableNaturalLanguages = undefined;
            $scope.selectedAddableNaturalLanguage = undefined;

            $scope.shouldSpin = function() {
                return undefined === $scope.pkg || $scope.amSaving || undefined === $scope.translations;
            };

            $scope.isTranslationSelected = function(translation) {
                return $scope.selectedTranslation.naturalLanguage.code === translation.naturalLanguage.code;
            };

            function findOriginalTranslation(naturalLanguageCode) {
                var result = _.find(
                    $scope.originalTranslations,
                    function (t) {
                        return t.naturalLanguage.code === naturalLanguageCode;
                    }
                );

                if (!result) {
                    throw Error('was not able to find the original translation');
                }

                return result;
            }

            /**
             * <p>This colours the text of the language to indicate if the translation is missing, invalid or
             * has been supplied successfully.</p>
             */

            $scope.classesForTranslation = function(translation) {
                var classes = [];

                if (translation.title && translation.title.length) {
                   classes.push('text-success');
                }
                else {
                    classes.push('text-warning');
                }

                if($scope.isTranslationSelected(translation)) {
                    classes.push('selected-translation');
                }

                return classes;
            };

            $scope.goShowHelp = function () {
                $scope.showHelp = true;
            };

            $scope.isSubordinate = function () {
                return $scope.pkg && pkg.isSubordinate($scope.pkg.name);
            };

            $scope.goChooseTranslation = function (translation) {
                if (!translation) {
                    throw Error('the translation must be provided to select');
                }

                $scope.selectedTranslation = translation;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.editPkgIconForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditPkgLocalization($scope.pkg))
                ]);
            }

            function setupTranslations() {
                referenceData.naturalLanguages().then(
                    function (naturalLanguageData) {

                        // bring in titles for the natural languages.

                        function updateNaturalLanguageOptionsTitles() {
                            _.each(naturalLanguageData, function (nl) {
                                messageSource.get(userState.naturalLanguageCode(), 'naturalLanguage.' + nl.code).then(
                                    function (value) {
                                        nl.title = value;
                                    },
                                    function () {
                                        $log.error('unable to get the localized name for the natural language \''+nl.code+'\'');
                                    }
                                );
                            });
                        }

                        updateNaturalLanguageOptionsTitles();

                        // now we need to get the _existing_ translations for the package.

                        remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_PKG,
                            'get-pkg-localizations',
                            {
                                pkgName: $routeParams.name,
                                naturalLanguageCodes : _.map(
                                    naturalLanguageData,
                                    function (d) {
                                        return d.code;
                                    }
                                )
                            }
                        )
                            .then(
                            function (pkgLocalizationsData) {

                                function hasData(translation) {
                                    return (translation.title && translation.title.length) ||
                                        (translation.summary && translation.summary.length) ||
                                        (translation.description && translation.description.length);
                                }

                                // now merge the data about the various natural languages together with the data about
                                // the packages existing localizations and we should have enough working data to setup
                                // an internal data model.

                                $scope.translations = _.filter(
                                    _.map(
                                        naturalLanguageData,
                                        function (d) {

                                            var pkgLocalizationData = _.findWhere(
                                                pkgLocalizationsData.pkgLocalizations,
                                                { naturalLanguageCode: d.code }
                                            );

                                            return {
                                                naturalLanguage: d,
                                                title: pkgLocalizationData ? pkgLocalizationData.title : '',
                                                summary: pkgLocalizationData ? pkgLocalizationData.summary : '',
                                                description: pkgLocalizationData ? pkgLocalizationData.description : '',
                                                wasEdited: false
                                            };
                                        }
                                    ),
                                    function (translation) {
                                        return translation.naturalLanguage.hasData ||
                                            translation.naturalLanguage.hasLocalizationMessages ||
                                            hasData(translation);
                                    }
                                );

                                $scope.originalTranslations = angular.copy($scope.translations);

                                // to default select the translation; try to find the current language if there
                                // is a stored value for the current language.

                                $scope.selectedTranslation = _.find($scope.translations, function(t) {
                                   return hasData(t) && t.naturalLanguage.code === userState.naturalLanguageCode();
                                });

                                if (!$scope.selectedTranslation) {
                                    $scope.selectedTranslation = _.find($scope.translations, function(t) {
                                        return hasData(t);
                                    });
                                }

                                if (!$scope.selectedTranslation) {
                                    $scope.selectedTranslation = _.find($scope.translations, function(t) {
                                        return t.naturalLanguage.code === userState.naturalLanguageCode();
                                    });
                                }

                                if (!$scope.selectedTranslation) {
                                    $scope.selectedTranslation = $scope.translations[0];
                                }

                                $log.info('did setup translations');

                                // get together a list of natural languages that could be added because they are not
                                // already in the list shown.

                                $scope.addableNaturalLanguages = _.filter(
                                    naturalLanguageData,
                                    function (d) {
                                        return d.code !== constants.NATURALLANGUAGECODE_ENGLISH && !_.find(
                                                $scope.translations,
                                                function (t) {
                                                    return d.code === t.naturalLanguage.code;
                                                }
                                            );
                                    }
                                );

                                if (0 !== $scope.addableNaturalLanguages.length) {
                                    $scope.selectedAddableNaturalLanguage = $scope.addableNaturalLanguages[0];
                                }

                            },
                            errorHandling.handleRemoteProcedureCallError
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
                pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                    function (result) {
                        $scope.pkg = result;
                        $log.info('found '+result.name+' pkg');
                        refreshBreadcrumbItems();
                    },
                    errorHandling.handleRemoteProcedureCallError
                );
            }

            /**
             * <p>This method will open a new window with the URL to show the localization of any version of this
             * package as was defined in the HPKR.</p>
             */

            $scope.goShowAnyPkgVersionLocalizations = function() {
                var item = breadcrumbFactory.createViewPkgVersionLocalization($scope.pkg);
                var url = window.location.origin + breadcrumbFactory.toFullPath(
                        item,
                        {
                            'banner': 'false',
                            'breadcrumbs': 'false'
                        }
                    );

                var opts = [
                    [ 'height', '480' ],
                    [ 'width',  '640' ],
                    [ 'menubar', 'false' ],
                    [ 'location', 'false' ],
                    [ 'scrollbars', '1' ],
                    [ 'toolbar', 'false' ],
                    [ 'personalbar', 'false' ],
                    [ 'status', 'false' ]
                ];

                window.open(
                    url,
                    _.uniqueId('ShowAnyPkgVersionLocalization'),
                    _.map(opts, function(o) {
                        return o.join('=')
                    }).join(',')
                );
            };

            // --------------------------
            // ADD A NATURAL LANGUAGE

            /**
             * <p>Add a new translation into the list based on a natural language.  This will also remove the
             * added language from the addable languages list.  It will then choose a new selected language.
             * </p>
             */

            $scope.goAddSelectedAddableNaturalLanguage = function () {

                var translation = {
                    naturalLanguage: $scope.selectedAddableNaturalLanguage,
                    title: '',
                    wasEdited: false
                };

                $scope.translations.push(translation);
                $scope.originalTranslations.push(_.clone(translation));

                $scope.addableNaturalLanguages = _.without(
                    $scope.addableNaturalLanguages,
                    $scope.selectedAddableNaturalLanguage);

                $scope.selectedAddableNaturalLanguage = $scope.addableNaturalLanguages.length ? $scope.addableNaturalLanguages[0] : undefined;

                $scope.goChooseTranslation(translation);
            };

            // --------------------------
            // SAVE CHANGES

            /**
             * <p>It is possible to save the translations if something has been edited and if there are no validity
             * problems with the translations.</p>
             */

            $scope.canSave = function () {
                return !!_.findWhere(
                        $scope.translations,
                        { wasEdited : true }
                    );
            };

            /**
             * <p>This method will persist those changes to the translations back into the server.</p>
             */

            $scope.saveEditedLocalizations = function() {

                if (!$scope.canSave()) {
                    throw Error('not possible to save edited localizations');
                }

                $scope.amSaving = true;

                remoteProcedureCall.call(
                    constants.ENDPOINT_API_V2_PKG,
                    'update-pkg-localization',
                    {
                        pkgName: $routeParams.name,
                        pkgLocalizations: _.map(
                            _.filter(
                                $scope.translations,
                                function (t) { return t.wasEdited; }
                            ),
                            function (t) {
                                return {
                                    naturalLanguageCode : t.naturalLanguage.code,
                                    title : t.title,
                                    summary : t.summary,
                                    description : t.description
                                };
                            }
                        )
                    }
                ).then(
                    function () {
                        $log.info('updated localization on '+$routeParams.name+' pkg');
                        breadcrumbs.popAndNavigate();
                    },
                    errorHandling.handleRemoteProcedureCallError
                );

            };

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
                function (newValue, oldValue) {
                    if (oldValue) {

                        function compareLocalizationElements(o1,o2) {

                            function norm(s) {
                                return !s ? '' : s;
                            }

                            return  norm(o1.title) === norm(o2.title) &&
                                norm(o1.summary) === norm(o2.summary) &&
                                norm(o1.description) === norm(o2.description);
                        }

                        if (oldValue.naturalLanguage.code === newValue.naturalLanguage.code &&
                            !compareLocalizationElements(oldValue,newValue)) {

                            // quick check to see if the new values equal the original values.

                            var originalTranslation = findOriginalTranslation(newValue.naturalLanguage.code);
                            $scope.selectedTranslation.wasEdited = !compareLocalizationElements(originalTranslation,newValue);
                        }
                    }
                },
                true);

        }
    ]
);
