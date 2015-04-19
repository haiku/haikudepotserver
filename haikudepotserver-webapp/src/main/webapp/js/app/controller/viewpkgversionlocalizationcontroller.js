/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This controller allows the user to simply view the various localizations that have been
 * prepared for this package version.</p>
 */

angular.module('haikudepotserver').controller(
    'ViewPkgVersionLocalizationController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','pkgIcon','errorHandling',
        'breadcrumbs','breadcrumbFactory','userState','referenceData','pkg','messageSource',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,pkgIcon,errorHandling,
            breadcrumbs,breadcrumbFactory,userState,referenceData,pkg,messageSource) {

            $scope.showHelp = false;
            $scope.pkg = undefined;
            $scope.pkgVersion = undefined;
            $scope.translations = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.pkg || undefined == $scope.translations;
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbFactory.createHome(),
                    breadcrumbFactory.createViewPkgWithSpecificVersionFromPkg($scope.pkg),
                    breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createViewPkgVersionLocalization($scope.pkg))
                ]);
            }

            function setupTranslations() {
                referenceData.naturalLanguages().then(
                    function(naturalLanguages) {

                        // bring in titles for the natural languages.

                        function updateNaturalLanguageTitles() {
                            _.each(naturalLanguages, function(nl) {
                                messageSource.get(userState.naturalLanguageCode(), 'naturalLanguage.' + nl.code).then(
                                    function(value) {
                                        nl.title = value;
                                    },
                                    function() {
                                        $log.error('unable to get the localized name for the natural language \''+nl.code+'\'');
                                    }
                                );
                            });
                        }

                        updateNaturalLanguageTitles();

                        // now we need to get the _existing_ translations for the package.

                        pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(
                            function(pkg) {

                                $scope.pkg = pkg;
                                var version = $scope.pkg.versions[0];

                                // turn it around so that the data structure is version focussed.
                                version.pkg = pkg;
                                $scope.pkgVersion = version;

                                jsonRpc.call(
                                    constants.ENDPOINT_API_V1_PKG,
                                    'getPkgVersionLocalizations',
                                    [{
                                        pkgName: $routeParams.name,
                                        major : version.major,
                                        minor : version.minor,
                                        micro : version.micro,
                                        preRelease : version.preRelease,
                                        revision : version.revision,
                                        naturalLanguageCodes : _.map(
                                            naturalLanguages,
                                            function(d) {
                                                return d.code;
                                            }
                                        ),
                                        architectureCode : $routeParams.architectureCode
                                    }]
                                ).then(
                                    function(naturalLanguageData) {
                                        $scope.translations = _.sortBy(
                                            _.filter(
                                                _.map(
                                                    naturalLanguageData.pkgVersionLocalizations,
                                                    function (pvl) {
                                                        return {
                                                            naturalLanguage: _.findWhere(naturalLanguages, { code: pvl.naturalLanguageCode }),
                                                            summary: pvl.summary,
                                                            description: pvl.description
                                                        };
                                                    }
                                                ),
                                                function(translation) {
                                                    return translation.summary && !!translation.summary.length &&
                                                        translation.description && !!translation.description.length;
                                                }
                                            ),
                                            function(translation) {
                                                return translation.naturalLanguage.code;
                                            }
                                        );

                                        refreshBreadcrumbItems();
                                    },
                                    function(jsonRpcErrorEnvelope) {
                                        $log.error('unable to get the package localizations');
                                        errorHandling.handleJsonRpcError(jsonRpcErrorEnvelope);
                                    }

                                )
                            },
                            function() {
                                $log.error('unable to get the package with specific route parameters');
                                errorHandling.navigateToError();
                            }
                        );

                    },
                    function() {
                        $log.error('unable to get the natural languages');
                        errorHandling.navigateToError();
                    }
                );
            }

            // --------------------------
            // INIT

            setupTranslations();

        }
    ]
);