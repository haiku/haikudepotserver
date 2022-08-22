/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will show a natural language code and will also allow the user to choose a natural
 * language from a list.</p>
 */

angular.module('haikudepotserver').directive(
    'naturalLanguageChooser',
        function() {
            return {
                restrict: 'E',
                templateUrl: '/__js/app/directivetemplate/naturallanguagechooser.html',
                replace: true,
                scope: {
                    naturalLanguageCode: '=',
                    naturalLanguageTitleClass: '@'
                },
                controller: [
                    '$scope', '$log',
                    'referenceData','errorHandling','messageSource','userState',
                    function(
                        $scope,$log,
                        referenceData,errorHandling,messageSource,userState) {

                        $scope.showChooser = false;
                        $scope.requiresDataOrLocalizationMessages = true;
                        $scope.possibleNaturalLanguages = undefined;

                        var priorSelectedNaturalLanguageCodes = [];

                        function reset() {
                            $scope.requiresDataOrLocalizationMessages = true;
                            $scope.possibleNaturalLanguages = undefined;
                        }

                        function refreshNaturalLanguages() {

                            referenceData.naturalLanguages().then(
                                function (naturalLanguages) {

                                    currentNaturalLanguageCode = userState.naturalLanguageCode();

                                    $scope.possibleNaturalLanguages = _.map(
                                        _.filter(
                                            naturalLanguages,
                                            function(nl) {
                                                return !$scope.requiresDataOrLocalizationMessages ||
                                                    nl.hasData ||
                                                    nl.hasLocalizationMessages ||
                                                    nl.code == currentNaturalLanguageCode ||
                                                    _.contains(
                                                        priorSelectedNaturalLanguageCodes,
                                                        nl.code);
                                            }
                                        ),
                                        function(nl) {

                                            // get a copy so that latter manipulations don't stuff-up the
                                            // actual reference data.

                                            return _.extend(_.clone(nl), { title : nl.code });
                                        }
                                    );

                                    // get the localization for the languages.

                                    _.each(
                                        $scope.possibleNaturalLanguages,
                                        function(nl) {
                                            messageSource.get(
                                                    userState.naturalLanguageCode(),
                                                    'naturalLanguage.' + nl.code).then(
                                                function(title) {
                                                    nl.title = title;
                                                },
                                                function() {
                                                    nl.title = '???';
                                                }
                                            )
                                        }
                                    );

                                },
                                function () {
                                    $log.warning('unable to obtain the list of natural languages');
                                    errorHandling.navigateToError();
                                }
                            );
                        }

                        $scope.goHideChooser = function() {
                            reset();
                            $scope.showChooser = false;
                        };

                        $scope.goShowChooser = function () {

                            reset();
                            refreshNaturalLanguages();

                            // this will open up the modal dialog-box which shows all of the languages that the user
                            // is then able to choose from.

                            $scope.showChooser = true;

                        };

                        /**
                         * <p>This function gets hit when the use clicks on a natural language inside the chooser.</p>
                         */

                        $scope.goChoose = function(naturalLanguage) {
                            $scope.naturalLanguageCode = naturalLanguage.code;
                            reset();
                            $scope.showChooser = false;
                        };

                        $scope.isChosen = function(naturalLanguage) {
                            return !!$scope.naturalLanguageCode &&
                                naturalLanguage.code == $scope.naturalLanguageCode;
                        };

                        $scope.setRequiresDataOrLocalizationMessages = function(flag) {
                            reset();
                            $scope.requiresDataOrLocalizationMessages = flag;
                            refreshNaturalLanguages();
                        };

                        // keep track of the selections; they should be maintained as options

                        $scope.$watch(
                            'naturalLanguageCode',
                            function(newValue) {
                                if(newValue) {
                                    priorSelectedNaturalLanguageCodes.push(newValue);
                                }
                            }
                        );

                    }
                ]
            };
        }
);
