/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'AddEditUserRatingController',
    [
        '$scope','$log','$location','$routeParams',
        'remoteProcedureCall','constants','breadcrumbs','breadcrumbFactory','userState',
        'errorHandling','referenceData','pkg','messageSource',
        function(
            $scope,$log,$location,$routeParams,
            remoteProcedureCall,constants,breadcrumbs,breadcrumbFactory,userState,
            errorHandling,referenceData,pkg,messageSource) {

            if(!userState.user()) {
                throw Error('a user is required to add / edit user ratings on a package');
            }

            $scope.workingUserRating = undefined;
            $scope.userRatingStabilityOptions = undefined;
            $scope.didAttemptToSaveWithEmptyWorkingUserRating = false;
            $scope.userRatingPickerInputName = _.uniqueId("ratingPicker");
            $scope.userRatingPickerPossibleValues = [ 0,1,2,3,4,5 ];
            var amSaving = false;

            $scope.shouldSpin = function () {
                return undefined === $scope.workingUserRating || amSaving;
            };

            $scope.deriveFormControlsContainerClasses = function (name) {
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

                function (chain) {
                    referenceData.userRatingStabilities().then(
                        function (data) {

                            $scope.userRatingStabilityOptions = _.map(
                                data,
                                function (userRatingStability) {
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
                        function () { // logging done already
                            errorHandling.navigateToError();
                        }

                    )
                },

                // working rating data; either this is an add rating or it is an edit rating and in the latter
                // case we actually have to get the data for the rating downloaded.

                function (chain) {

                    function findUserRatingStabilityOptionByCode(code) {
                        var userRatingStabilityOption = _.findWhere(
                            $scope.userRatingStabilityOptions,
                            { code : code });

                        if (!userRatingStabilityOption) {
                            throw Error('unable to find the user rating stability option for; ' + code);
                        }

                        return userRatingStabilityOption;
                    }

                    // This function will consume the serialized data from the "AbstractGetUserRatingResult" DTO
                    // object and will turn that into a working data set for the form.

                    function assembleWorkingUserRatingFromApiResult(userRatingData) {
                        return {
                            code: userRatingData.code,
                            userRatingStabilityOption: findUserRatingStabilityOptionByCode(userRatingData.userRatingStabilityCode),
                            naturalLanguageCode: userRatingData.naturalLanguageCode,
                            user: userRatingData.user,
                            comment: userRatingData.comment,
                            rating: userRatingData.rating,
                            pkgVersion: userRatingData.pkgVersion
                        };
                    }

                    if($routeParams.code) {

                        // we will know the package based on the user rating that is found.

                      remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_USERRATING,
                            'get-user-rating',
                            { code: $routeParams.code }
                        ).then(
                            function (result) {
                                $scope.workingUserRating = assembleWorkingUserRatingFromApiResult(result);
                                fnChain(chain);
                            },
                            errorHandling.handleRemoteProcedureCallError
                        );

                    }
                    else {

                        pkg.getPkgWithSpecificVersionFromRouteParams($routeParams, false).then(

                            // the package must be been supplied in the path so we will fetch the package and then
                            // fabricate a working user rating around that.

                            function (pkg) {

                                // there is a possibility that this user has already made a user rating on this package
                                // version.  If this is the case then they should not be allowed to add another -- they
                                // need to edit the one that they already have.

                              remoteProcedureCall.call(
                                    constants.ENDPOINT_API_V2_USERRATING,
                                    'get-user-rating-by-user-and-pkg-version',
                                    {
                                        userNickname: userState.user().nickname,
                                        pkgName: pkg.name,
                                        repositorySourceCode : pkg.versions[0].repositorySourceCode,
                                        pkgVersionArchitectureCode: pkg.versions[0].architectureCode,
                                        pkgVersionMajor: pkg.versions[0].major,
                                        pkgVersionMinor: pkg.versions[0].minor,
                                        pkgVersionMicro: pkg.versions[0].micro,
                                        pkgVersionPreRelease: pkg.versions[0].preRelease,
                                        pkgVersionRevision: pkg.versions[0].revision
                                    }
                                )
                                    .then(
                                    function (existingUserRatingData) {
                                        $log.info('user was adding user rating, but one already existed; will edit that instead');
                                        $scope.workingUserRating = assembleWorkingUserRatingFromApiResult(existingUserRatingData);
                                        fnChain(chain);
                                    },
                                    function (remoteProcedureCallErrorEnvelope) {
                                        if (remoteProcedureCallErrorEnvelope.code === remoteProcedureCall.errorCodes.OBJECTNOTFOUND) {

                                            // no existing user rating for this package version -> will make a new
                                            // one.

                                            if (!pkg.versions[0].isLatest) {
                                                throw Error('it is only possible to add a user rating to the latest version of a package.');
                                            }

                                            // turn the package data inside out so that we have a pkgVersion data structure.
                                            var assembledPkgVersion = pkg.versions[0];
                                            assembledPkgVersion.pkg = { name : pkg.name };

                                            $scope.workingUserRating = {
                                                userRatingStabilityOption: findUserRatingStabilityOptionByCode(null),
                                                naturalLanguageCode: userState.naturalLanguageCode(),
                                                user: userState.user(),
                                                userRatingStability: null,
                                                rating: null,
                                                pkgVersion: assembledPkgVersion
                                            };

                                            fnChain(chain);

                                        }
                                        else {
                                            errorHandling.handleRemoteProcedureCallError(remoteProcedureCallErrorEnvelope);
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
                        breadcrumbFactory.createHome(),
                        breadcrumbFactory.createViewPkgWithSpecificVersionFromPkgVersion($scope.workingUserRating.pkgVersion)
                    ];

                    if ($scope.workingUserRating.code) {
                        b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createEditUserRating($scope.workingUserRating)));
                    }
                    else {
                        b.push(breadcrumbFactory.applyCurrentLocation(breadcrumbFactory.createAddUserRating({
                            name: $scope.workingUserRating.pkgVersion.pkg.name,
                            versions: [
                                $scope.workingUserRating.pkgVersion
                            ]
                        })));
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

                if ($scope.workingUserRating.comment) {
                    $scope.workingUserRating.comment = $scope.workingUserRating.comment.trim();
                }

                if ($scope.addEditUserRatingForm.$invalid) {
                    throw Error('expected the save of a user rating to only to be possible if the form is valid');
                }

                // there is the possibility that the user has attempted to create a user rating with no data!  This
                // should be stopped!

                if (isEmptyWorkingUserRating()) {
                    $scope.didAttemptToSaveWithEmptyWorkingUserRating = true; // shows-up in UI
                }
                else {

                    amSaving = true;

                    if ($scope.workingUserRating.code) {
                      remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_USERRATING,
                            'update-user-rating',
                            {
                                active: true, // in case an older rating is being edited that was de-activated
                                code: $scope.workingUserRating.code,
                                naturalLanguageCode: $scope.workingUserRating.naturalLanguageCode,
                                userRatingStabilityCode: $scope.workingUserRating.userRatingStabilityOption.code,
                                comment: $scope.workingUserRating.comment,
                                rating: $scope.workingUserRating.rating,
                                filter: [
                                    'NATURALLANGUAGE',
                                    'USERRATINGSTABILITY',
                                    'COMMENT',
                                    'RATING',
                                    'ACTIVE'
                                ]
                            }
                        ).then(
                            function () {
                                $log.info('did update user rating; ' + $scope.workingUserRating.code);
                                breadcrumbs.popAndNavigate();
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                                amSaving = false;
                            }
                        );
                    }
                    else {
                      remoteProcedureCall.call(
                            constants.ENDPOINT_API_V2_USERRATING,
                            "create-user-rating",
                            {
                                naturalLanguageCode: $scope.workingUserRating.naturalLanguageCode,
                                repositorySourceCode: $scope.workingUserRating.pkgVersion.repositorySourceCode,
                                userNickname: $scope.workingUserRating.user.nickname,
                                userRatingStabilityCode: $scope.workingUserRating.userRatingStabilityOption.code,
                                comment: $scope.workingUserRating.comment,
                                rating: $scope.workingUserRating.rating,
                                pkgName: $scope.workingUserRating.pkgVersion.pkg.name,
                                pkgVersionType: 'LATEST',
                                pkgVersionArchitectureCode: $scope.workingUserRating.pkgVersion.architectureCode
                            }
                        ).then(
                            function (data) {
                                $log.info('did create user rating; ' + data.code);
                                breadcrumbs.pop();
                                breadcrumbs.pushAndNavigate(breadcrumbFactory.createViewUserRating(data)); // just needs the code
                            },
                            function (err) {
                                errorHandling.handleRemoteProcedureCallError(err);
                                amSaving = false;
                            }
                        );
                    }
                }

            }

        }
    ]
);
