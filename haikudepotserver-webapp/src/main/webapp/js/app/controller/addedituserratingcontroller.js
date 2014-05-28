/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

// WORK IN PROGRESS!

angular.module('haikudepotserver').controller(
    'AddEditUserRatingController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','userState',
        'errorHandling','referenceData','pkg','messageSource',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,userState,
            errorHandling,referenceData,pkg,messageSource) {

            if(!userState.user()) {
                throw 'a user is required to add / edit user ratings on a package';
            }

            $scope.workingUserRating = undefined;
            $scope.userRatingStabilityOptions = undefined;
            $scope.naturalLanguageOptions = undefined;
            $scope.didAttemptToSaveWithEmptyWorkingUserRating = false;
            $scope.userRatingPickerInputName = _.uniqueId("ratingPicker");
            $scope.userRatingPickerPossibleValues = [ 0,1,2,3,4,5 ];
            var amSaving = false;

            $scope.shouldSpin = function() {
                return undefined == $scope.workingUserRating || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function(name) {
                return $scope.addEditUserRatingForm[name].$invalid ? ['form-control-group-error'] : [];
            };

            // ---------------------------
            // GET WORKING DATA

            // this function will execute the last item in the chain and pass the remainder of
            // the chain into the function to be latterly executed.  This allows a series of
            // discrete functions of work to proceed serially.

            function fnChain(chain) {
                if(chain && chain.length) {
                    chain.shift()(chain);
                }
            }

            // specific localizations

            function updateNaturalLanguageOptionsTitles() {
                _.each($scope.naturalLanguageOptions, function(nl) {
                    messageSource.get(userState.naturalLanguageCode(), 'naturalLanguage.' + nl.code).then(
                        function (value) {
                            nl.title = value;
                        },
                        function () {
                            $log.error('unable to get the localized name for the natural language \'' + nl.code + '\'');
                        }
                    );
                });
            }

            function updateUserRatingStabilityOptionsTitles() {
                _.each($scope.userRatingStabilityOptions, function(userRatingStabilityOption) {
                    var key = 'addEditUserRating.userRatingStability.none';

                    if(userRatingStabilityOption.code) {
                        key = 'userRatingStability.' + userRatingStabilityOption.code + '.title';
                    }

                    messageSource.get(userState.naturalLanguageCode(), key).then(
                        function (value) {
                            userRatingStabilityOption.title = value;
                        },
                        function () {
                            $log.error('unable to get the localized name for the user rating stability option \'' + userRatingStabilityOption.code + '\'');
                        }
                    );
                });
            }

            // load up the initial data for the page.

            fnChain([

                // user rating stabilities reference data.
                // TODO; generalize this code and factor out

                function(chain) {
                    referenceData.userRatingStabilities().then(
                        function(data) {

                            $scope.userRatingStabilityOptions = _.map(
                                data,
                                function(userRatingStability) {
                                    return {
                                        code : userRatingStability.code,
                                        title : userRatingStability.code
                                    }
                                }
                            );

                            $scope.userRatingStabilityOptions.unshift({
                                code : null,
                                title : '---'
                            });

                            updateUserRatingStabilityOptionsTitles();
                            fnChain(chain);
                        },
                        function() { // logging done already
                            errorHandling.navigateToError();
                        }

                    )
                },

                // natural language options; allows the user to select what language they would like to add
                // their user rating in.
                // TODO: generalized code with edit user.

                function(chain) {
                    referenceData.naturalLanguages().then(
                        function (fetchedNaturalLanguages) {

                            $scope.naturalLanguageOptions = _.map(
                                fetchedNaturalLanguages,
                                function (nl) {
                                    return {
                                        code: nl.code,
                                        title: nl.name
                                    }
                                }
                            );

                            updateNaturalLanguageOptionsTitles();
                            fnChain(chain);
                        },
                        function () { // already logged.
                            errorHandling.navigateToError();
                        }
                    )
                },

                // working rating data; either this is an add rating or it is an edit rating and in the latter
                // case we actually have to get the data for the rating downloaded.

                function(chain) {

                    function findNaturalLanguageOptionByCode(code) {
                        var naturalLanguageOption = _.findWhere(
                            $scope.naturalLanguageOptions,
                            { code : code });

                        if(!naturalLanguageOption) {
                            throw 'unable to find the natural language option for; ' + code;
                        }

                        return naturalLanguageOption;
                    }

                    function findUserRatingStabilityOptionByCode(code) {
                        var userRatingStabilityOption = _.findWhere(
                            $scope.userRatingStabilityOptions,
                            { code : code });

                        if(!userRatingStabilityOption) {
                            throw 'unable to find the user rating stability option for; ' + code;
                        }

                        return userRatingStabilityOption;
                    }

                    // This function will consume the serialized data from the "AbstractGetUserRatingResult" DTO
                    // object and will turn that into a working data set for the form.

                    function assembleWorkingUserRatingFromApiResult(userRatingData) {
                        return {
                            code: userRatingData.code,
                            userRatingStabilityOption: findUserRatingStabilityOptionByCode(userRatingData.userRatingStabilityCode),
                            naturalLanguageOption: findNaturalLanguageOptionByCode(userRatingData.naturalLanguageCode),
                            user: userRatingData.user,
                            comment: userRatingData.comment,
                            rating: userRatingData.rating,
                            pkgVersion: userRatingData.pkgVersion
                        };
                    }

                    if($routeParams.code) {

                        // we will know the package based on the user rating that is found.

                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_USERRATING,
                            'getUserRating',
                            [{ code: $routeParams.code }]
                        ).then(
                            function(result) {
                                $scope.workingUserRating = assembleWorkingUserRatingFromApiResult(result);
                                fnChain(chain);
                            },
                            function(err) {
                                errorHandling.handleJsonRpcError(err);
                            }
                        );

                    }
                    else {

                        pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(

                            // the package must be been supplied in the path so we will fetch the package and then
                            // fabricate a working user rating around that.

                            function(pkg) {

                                // there is a possibility that this user has already made a user rating on this package
                                // version.  If this is the case then they should not be allowed to add another -- they
                                // need to edit the one that they already have.

                                jsonRpc.call(
                                    constants.ENDPOINT_API_V1_USERRATING,
                                    'getUserRatingByUserAndPkgVersion',
                                    [
                                        {
                                            userNickname: userState.user().nickname,
                                            pkgName: pkg.name,
                                            pkgVersionArchitectureCode: pkg.versions[0].architectureCode,
                                            pkgVersionMajor: pkg.versions[0].major,
                                            pkgVersionMinor: pkg.versions[0].minor,
                                            pkgVersionMicro: pkg.versions[0].micro,
                                            pkgVersionPreRelease: pkg.versions[0].preRelease,
                                            pkgVersionRevision: pkg.versions[0].revision
                                        }
                                    ]
                                )
                                    .then(
                                    function(existingUserRatingData) {
                                        $log.info('user was adding user rating, but one already existed; will edit that instead');
                                        $scope.workingUserRating = assembleWorkingUserRatingFromApiResult(existingUserRatingData);
                                        fnChain(chain);
                                    },
                                    function(jsonRpcErrorEnvelope) {
                                        if(jsonRpcErrorEnvelope.code == jsonRpc.errorCodes.OBJECTNOTFOUND) {

                                            // no existing user rating for this package version -> will make a new
                                            // one.

                                            if(!pkg.versions[0].isLatest) {
                                                throw 'it is only possible to add a user rating to the latest version of a package.';
                                            }

                                            $scope.workingUserRating = {
                                                userRatingStabilityOption: findUserRatingStabilityOptionByCode(null),
                                                naturalLanguageOption: findNaturalLanguageOptionByCode(userState.naturalLanguageCode()),
                                                user: userState.user(),
                                                userRatingStability: null,
                                                rating: null,
                                                pkg: pkg
                                            };

                                            fnChain(chain);

                                        }
                                        else {
                                            errorHandling.handleJsonRpcError(jsonRpcErrorEnvelope);
                                        }
                                    }
                                );
                            },
                            function() {
                                errorHandling.navigateToError(); // already logged.
                            }
                        );
                    }
                },

                // breadcrumbs

                function(chain) {
                    var b = [
                        breadcrumbs.createHome(),
                        breadcrumbs.createViewPkgWithSpecificVersionFromPkgVersion($scope.workingUserRating.pkgVersion)
                    ];

                    if($scope.workingUserRating.code) {
                        // TODO; push view
                        b.push({
                            titleKey : 'breadcrumb.editUserRating.title',
                            path : $location.path()
                        });
                    }
                    else {
                        b.push({
                            titleKey : 'breadcrumb.addUserRating.title',
                            path : $location.path()
                        })
                    }

                    breadcrumbs.mergeCompleteStack(b);

                    fnChain(chain);
                }

            ]);

            // ---------------------------
            // SAVING / DATA

            $scope.$watchCollection('workingUserRating', function() {
                $scope.didAttemptToSaveWithEmptyWorkingUserRating = false;
            });

            function isEmptyWorkingUserRating() {
                return (!$scope.workingUserRating.comment || !$scope.workingUserRating.comment.length) &&
                    !$scope.workingUserRating.rating &&
                    !$scope.workingUserRating.userRatingStabilityOption.code;
            }

            $scope.goSave = function() {

                if($scope.workingUserRating.comment) {
                    $scope.workingUserRating.comment = $scope.workingUserRating.comment.trim();
                }

                if($scope.addEditUserRatingForm.$invalid) {
                    throw 'expected the save of a user rating to only to be possible if the form is valid';
                }

                // there is the possibility that the user has attempted to create a user rating with no data!  This
                // should be stopped!

                if(isEmptyWorkingUserRating()) {
                    $scope.didAttemptToSaveWithEmptyWorkingUserRating = true; // shows-up in UI
                }
                else {

                    amSaving = true;

                    if ($scope.workingUserRating.code) {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_USERRATING,
                            'updateUserRating',
                            [
                                {
                                    code: $scope.workingUserRating.code,
                                    naturalLanguageCode: $scope.workingUserRating.naturalLanguageOption.code,
                                    userRatingStabilityCode: $scope.workingUserRating.userRatingStabilityOption.code,
                                    comment: $scope.workingUserRating.comment,
                                    rating: $scope.workingUserRating.rating,
                                    filter: [
                                        'NATURALLANGUAGE',
                                        'USERRATINGSTABILITY',
                                        'COMMENT',
                                        'RATING'
                                    ]
                                }
                            ]
                        ).then(
                            function () {
                                $log.info('did update user rating; ' + $scope.workingUserRating.code);
                                breadcrumbs.popAndNavigate();
                            },
                            function (err) {
                                errorHandling.handleJsonRpcError(err);
                                amSaving = false;
                            }
                        );
                    }
                    else {
                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_USERRATING,
                            "createUserRating",
                            [
                                {
                                    naturalLanguageCode: $scope.workingUserRating.naturalLanguageOption.code,
                                    userNickname: $scope.workingUserRating.user.nickname,
                                    userRatingStabilityCode: $scope.workingUserRating.userRatingStabilityOption.code,
                                    comment: $scope.workingUserRating.comment,
                                    rating: $scope.workingUserRating.rating,
                                    pkgName: $scope.workingUserRating.pkg.name,
                                    pkgVersionType: 'LATEST',
                                    pkgVersionArchitectureCode: $scope.workingUserRating.pkg.versions[0].architectureCode
                                }
                            ]
                        ).then(
                            function (data) {
                                $log.info('did create user rating; ' + data.code);
                                breadcrumbs.pop();
                                $location.path(breadcrumbs.createViewUserRating(data).path).search({}); // just needs the code
                            },
                            function (err) {
                                errorHandling.handleJsonRpcError(err);
                                amSaving = false;
                            }
                        );
                    }
                }

            }

        }
    ]
);