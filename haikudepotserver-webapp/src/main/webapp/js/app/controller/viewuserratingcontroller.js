/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').controller(
    'ViewUserRatingController',
    [
        '$scope','$log','$location','$routeParams',
        'jsonRpc','constants','errorHandling','breadcrumbs',
        function(
            $scope,$log,$location,$routeParams,
            jsonRpc,constants,errorHandling,breadcrumbs) {

            var amUpdating = false;
            $scope.breadcrumbItems = undefined;
            $scope.userRating = undefined;

            $scope.shouldSpin = function() {
                return undefined == $scope.userRating || amUpdating;
            };

            $scope.hasRating = function() {
                return $scope.userRating &&
                    angular.isNumber($scope.userRating.rating);
            };

            function refreshBreadcrumbItems() {
                breadcrumbs.mergeCompleteStack([
                    breadcrumbs.createHome(),
                    breadcrumbs.createViewPkgWithSpecificVersionFromPkgVersion($scope.userRating.pkgVersion),
                    breadcrumbs.createViewUserRating($scope.userRating)
                ]);
            }

            function refreshUserRating() {
                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING,
                    "getUserRating",
                    [{ code : $routeParams.code }]
                ).then(
                    function(userRatingData) {
                        $scope.userRating = userRatingData;
                        refreshBreadcrumbItems();
                        $log.info('fetched user rating; '+userRatingData.code);
                    },
                    function(err) {
                        errorHandling.handleJsonRpcError(err);
                    }
                );
            }

            refreshUserRating();

            // --------------------------------
            // ACTIONS

            $scope.goEdit = function() {
                $location.path(breadcrumbs.createEditUserRating($scope.userRating).path).search({});
            };

            $scope.canDeactivate = function() {
                return $scope.userRating && $scope.userRating.active;
            };

            $scope.canActivate = function() {
                return $scope.userRating && !$scope.userRating.active;
            };

            $scope.goActivate = function() {
                setActive(true);
            };

            $scope.goDeactivate = function() {
                setActive(false);
            };

            /**
             * <P>This function will configure the user rating to be either active or inactive.</p>
             */

            function setActive(flag) {

                amUpdating = true;

                jsonRpc.call(
                    constants.ENDPOINT_API_V1_USERRATING,
                    "updateUserRating",
                    [{
                        code : $scope.userRating.code,
                        filter : [ 'ACTIVE' ],
                        active : !!flag
                    }]
                ).then(
                    function() {
                        $log.info('did update the active flag on the user rating to; ' + flag);
                        $scope.userRating.active = flag;
                        amUpdating = false;
                    },
                    function(err) {
                        $log.info('an error arose updating the active flag on the user rating');
                        errorHandling.handleJsonRpcError(err);
                    }
                );

            }

        }
    ]
);