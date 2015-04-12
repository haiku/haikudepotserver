/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will show a pkg version localization for any architecture for the supplied pkg.  This
 * directive is only expected to be used in the edit pkg localization controller / page.</p>
 */

angular.module('haikudepotserver').directive(
    'anyPkgVersionLocalization',
    function() {
        return {
            restrict: 'E',
            templateUrl: '/js/app/directive/anypkgversionlocalization.html',
            replace: true,
            scope: {
                pkg: '=',
            },
            controller: [
                '$scope', '$log', '$q',
                'referenceData','errorHandling','constants','jsonRpc',
                function(
                    $scope,$log, $q,
                    referenceData,errorHandling,constants,jsonRpc) {

                    $scope.anyEnglishPkgVersionLocalization = undefined;

                    /**
                     * <p>This function will fetch any english localization for any version of the package
                     * being inspected.  It may be necessary to do something a bit more sophisticated here
                     * at some point, but this will be useful for the time being.</p>
                     *
                     * <p>This tries first to find data for the default architecture (likely) and failing
                     * that will try the other architectures instead.</p>
                     */

                    function refetchAnyEnglishPkgVersionLocalization() {

                        function findEnglish(result) {
                            return _.findWhere(
                                result.pkgVersionLocalizations,
                                { naturalLanguageCode : constants.NATURALLANGUAGECODE_ENGLISH }
                            );
                        }

                        function tryFetchAnyEnglishPkgVersionLocalizationForArchitecture(architectureCode) {

                            var deferred = $q.defer();

                            jsonRpc.call(
                                constants.ENDPOINT_API_V1_PKG,
                                'getPkgVersionLocalizations',
                                [{
                                    pkgName: $scope.pkg.name,
                                    naturalLanguageCodes : [ constants.NATURALLANGUAGECODE_ENGLISH ],
                                    architectureCode : architectureCode
                                }]
                            )
                                .then(
                                function(data) {
                                    var en = findEnglish(data);
                                    deferred.resolve(en ? en : null);
                                },
                                function(err) {
                                    switch(err.code) {

                                        case jsonRpc.errorCodes.OBJECTNOTFOUND:
                                            deferred.resolve(null);
                                            break;

                                        default:
                                            $log.error('unable to try to get a pkg version localization for architecture; ' + architectureCode);
                                            errorHandling.handleJsonRpcError(err);
                                            break;
                                    }
                                }
                            );

                            return deferred.promise;
                        }

                        // recurses asynchornously.

                        function tryFetchAnyEnglishPkgVersionLocalizationForArchitectures(architectures) {

                            var deferred = $q.defer();

                            if(architectures.length) {
                                $log.info('no pkg version localizations could be found');
                                deferred.resolve(null);
                            }
                            else {
                                tryFetchAnyEnglishPkgVersionLocalizationForArchitecture(architectures.shift().code).then(
                                    function(data) {
                                        var en = !!data ? findEnglish(data) : null;

                                        if(null==en) {
                                            tryFetchAnyEnglishPkgVersionLocalizationForArchitectures(architectures);
                                        }
                                        else {
                                            deferred.resolve(en);
                                        }
                                    },
                                    function() {
                                        errorHandling.navigateToError();
                                    }
                                );
                            }

                            return deferred.promise;
                        }

                        if($scope.pkg) {

                            tryFetchAnyEnglishPkgVersionLocalizationForArchitecture(constants.ARCHITECTURE_CODE_DEFAULT).then(
                                function (data) {
                                    if (data) {
                                        $log.info('did fetch any english pkg version localization for default architecture');
                                        $scope.anyEnglishPkgVersionLocalization = data;
                                    }
                                    else {
                                        referenceData.architectures().then(
                                            function (architectures) {

                                                tryFetchAnyEnglishPkgVersionLocalizationForArchitectures(
                                                    _.reject(
                                                        _.clone(architectures),
                                                        function (a) {
                                                            return constants.ARCHITECTURE_CODE_DEFAULT == a.code ||
                                                                    'source' == a.code;
                                                        }
                                                    )
                                                ).then(
                                                    function (data) {
                                                        $log.info('did find any english package version localization');
                                                        $scope.anyEnglishPkgVersionLocalization = data;
                                                    },
                                                    function () {
                                                        errorHandling.navigateToError();
                                                    }
                                                );
                                            },
                                            function () {
                                                errorHandling.navigateToError();
                                            }
                                        );
                                    }
                                }
                            );
                        }
                        else {
                            $scope.anyEnglishPkgVersionLocalization = null;
                        }
                    }

                    $scope.$watch('pkg', function() {
                        refetchAnyEnglishPkgVersionLocalization();
                    });

                    refetchAnyEnglishPkgVersionLocalization();

                }
            ]
        };
    }
);