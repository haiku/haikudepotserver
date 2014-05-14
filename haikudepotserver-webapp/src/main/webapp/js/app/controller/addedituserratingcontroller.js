/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

// WORK IN PROGRESS!

angular.module('haikudepotserver').controller(
    'AddEditUserRatingController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','breadcrumbs','userState','errorHandling','referenceData','pkg',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,breadcrumbs,userState,errorHandling,referenceData,pkg) {

            $scope.workingUserRating = undefined;
            $scope.userRatingStabilities = undefined;
            $scope.amEditing = !!$routeParams.userRatingCode;
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

            // load up the initial data for the page.

            fnChain([

                // user rating stabilities reference data.

                function(chain) {
                    referenceData.userRatingStabilities().then(
                        function(data) {
                            $scope.userRatingStabilities = data;
                            fnChain(chain);
                        },
                        function() { // logging done already
                            errorHandling.navigateToError();
                        }

                    )
                },

                // working rating data; either this is an add rating or it is an edit rating and in the latter
                // case we actually have to get the data for the rating downloaded.

                function(chain) {

                    if($routeParams.userRatingCode) {

                        // we will know the package based on the user rating that is found.

                        jsonRpc.call(
                            constants.ENDPOINT_API_V1_USERRATING,
                            'getUserRating',
                            [{ code: $routeParams.userRatingCode }]
                        ).then(
                            function(result) {

                                $scope.workingUserRating = {
                                    code: result.code,
                                    naturalLanguageCode: result.naturalLanguageCode,
                                    user: { nickname: result.userNickname },
                                    rating: 3,
                                    pkg: {
                                        name: result.pkgName,
                                        versions: [
                                            {
                                                architectureCode: result.pkgVersionArchitectureCode,
                                                major: release.pkgVersionMajor,
                                                minor: release.pkgVersionMinor,
                                                micro: release.pkgVersionMicro,
                                                preRelease: release.pkgVersionPreRelease,
                                                revision: release.pkgVersionRevision
                                            }
                                        ]
                                    }
                                };

                                if(result.userRatingStabilityCode) {
                                    $scope.workingUserRating.userRatingStability = _.findWhere(
                                        $scope.userRatingStabilities,
                                        { code : result.userRatingStabilityCode }
                                    );
                                }

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

                                if(!pkg.versions[0].isLatest) {
                                    throw 'it is only possible to add a user rating to the latest version of a package.';
                                }

                                $scope.workingUserRating = {
                                    naturalLanguageCode: userState.naturalLanguageCode(),
                                    user: userState.user(),
                                    userRatingStability: null,
                                    rating: null,
                                    pkg: pkg
                                };
                                fnChain(chain);
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
                        breadcrumbs.createViewPkgWithSpecificVersionFromPkg($scope.workingUserRating.pkg)
                    ];

                    if($scope.amEditing) {
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
            // SAVING

            $scope.goSave = function() {

                if($scope.addEditUserRating.$invalid) {
                    throw 'expected the save of a user rating to only to be possible if the form is valid';
                }

                amSaving = true;

                if($scope.amEditing) {
                    jsonRpc.call(
                        constants.ENDPOINT_API_V1_USERRATING,
                        'updateUserRating',
                        [{
                            code : $scope.workingUserRating.code,
                            naturalLanguageCode : $scope.workingUserRating.naturalLanguageCode,
                            userRatingStabilityCode : $scope.workingUserRating.userRatingStability ? $scope.workingUserRating.userRatingStability.code : null,
                            comment : $scope.workingUserRating.comment,
                            rating : $scope.workingUserRating.rating,
                            filter : [
                                'NATURALLANGUAGE',
                                'USERRATINGSTABILITY',
                                'COMMENT',
                                'RATING'
                            ]
                        }]
                    ).then(
                        function() {
                            $log.info('did update user rating; '+$scope.workingUserRating.code);
                            breadcrumbs.popAndNavigate();
                        },
                        function(err) {
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
                                naturalLanguageCode: $scope.workingUserRating.naturalLanguageCode,
                                userNickname: $scope.workingUserRating.user.nickname,
                                userRatingStabilityCode: $scope.workingUserRating.userRatingStability ? $scope.workingUserRating.userRatingStability.code : null,
                                comment: $scope.workingUserRating.comment,
                                rating: $scope.workingUserRating.rating,
                                pkgName: $scope.pkg.name,
                                pkgVersionType: 'LATEST'
                            }
                        ]
                    ).then(
                        function(data) {
                            $log.info('did create user rating; ' + data.code);
                            throw 'TODO; now view the user rating';
                        },
                        function(err) {
                            errorHandling.handleJsonRpcError(err);
                            amSaving = false;
                        }
                    );
                }

            }

        }
    ]
);