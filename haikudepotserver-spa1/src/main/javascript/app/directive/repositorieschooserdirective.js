/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive will allow the user to choose from a list of repositories.</p>
 */

angular.module('haikudepotserver').directive(
    'repositoriesChooser',
    function() {
        return {
            restrict: 'E',
            templateUrl: '/__js/app/directivetemplate/repositorieschooser.html',
            replace: true,
            scope: {
                repositories: '=',
                min: '@'
            },
            controller: [
                '$scope', '$log',
                'referenceData','errorHandling','messageSource','userState','repositoryService','miscService',
                function(
                    $scope,$log,
                    referenceData,errorHandling,messageSource,userState,repositoryService,miscService) {

                    var MAX_LABEL_LENGTH = 32;
                    var min = !!min && min.length ? parseInt('' + $scope.min) : 0;
                    $scope.showChooser = false;
                    $scope.label = '?';
                    $scope.possibleRepositoryOptions = undefined;

                    function reset() {
                        $scope.possibleRepositoryOptions = undefined;
                    }

                    // creates a 'pretty' label from the list of repositories.

                    function createRepositoriesLabel(repositories) {
                        var l = repositories ? repositories.length : 0;

                        if(0 == l) {
                            return '?';
                        }

                        if(l < 3) {
                            return _.pluck(repositories, 'name').join(', ');
                        }

                        var scratch = _.map(repositories, function(r) {
                            return {
                                'original' : r.name
                            };
                        });

                        miscService.abbreviate(scratch,'original','abbreviated');

                        if(l < 4) {
                            return _.pluck(scratch, 'abbreviated').join(', ');
                        }

                        return _.pluck(scratch.splice(0,4), 'abbreviated').join(', ') + '\u2026';
                    }

                    function refreshRepositoryOptions() {
                        repositoryService.getRepositories().then(
                            function (repos) {
                                $scope.possibleRepositoryOptions = _.map(
                                    repos,
                                    function(r) {
                                        return {
                                            repository : r,
                                            selected : !!_.findWhere($scope.repositories, { code : r.code })
                                        }
                                    }
                                );
                            },
                            function () {
                                $log.warning('unable to obtain the list of repositories');
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
                        refreshRepositoryOptions();

                        // this will open up the modal dialog-box which shows all of the languages that the user
                        // is then able to choose from.

                        $scope.showChooser = true;

                    };

                    function getSelectedRepositoryOptions() {
                        return _.filter(
                            $scope.possibleRepositoryOptions,
                            function(ro) {
                                return ro.selected;
                            }
                        );
                    }

                    $scope.isValid = function() {
                        return getSelectedRepositoryOptions().length > min;
                    };

                    /**
                     * This function is hit when the user confirms their repository selection.
                     */

                    $scope.goConfirm = function() {
                        $scope.repositories = _.map(
                            getSelectedRepositoryOptions(),
                            function(ro) {
                                return ro.repository;
                            }
                        );

                        reset();
                        $scope.showChooser = false;
                    };

                    // keep an eye on the list of repositories; if the list changes then create a new label that is
                    // constructed from the list of abbreviations.

                    $scope.$watch(
                        'repositories',
                        function(newValue) {
                            $scope.label = createRepositoriesLabel(newValue);
                        }
                    );

                }
            ]
        };
    }
);
