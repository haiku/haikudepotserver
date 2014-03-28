/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to render an icon for the pkg bound to it.  It expects that the size is also provided.
 * There may be restrictions on the size at the server end where the pkg icon image is ultimately delivered.  The
 * 'pkg' data structure is expected to have a 'name' property
 */

angular.module('haikudepotserver').directive('pkgIcon',function() {
        return {
            restrict: 'E',
            template:'<img width="{{size}}" height="{{size}}" ng-src="{{imgUrl}}"></img>',
            replace: true,
            scope: {
                size:'@',
                pkg:'='
            },
            controller:
                ['$scope','pkgIcon','constants',
                    function($scope,pkgIcon,constants) {

                        $scope.imgUrl = '';

                        function refreshImgUrl() {
                            if($scope.size && $scope.pkg) {
                                if(!$scope.pkg.name) {
                                    throw 'pkg does not contain a name to identify the pkg for the pkg-icon';
                                }
                                else {
                                    $scope.imgUrl = pkgIcon.url($scope.pkg, constants.MEDIATYPE_PNG, $scope.size);
                                }
                            }
                        }

                        $scope.$watch('size',function() {
                            refreshImgUrl();
                        });

                        $scope.$watch('pkg',function() {
                            refreshImgUrl();
                        });

                        // we keep an eye on the modify timestamp because if somebody changes the package then
                        // it could be that the icon has been the thing that has changed; for example, somebody
                        // may have opted to remove the package icon.

                        $scope.$watch('pkg.modifyTimestamp', function() {
                            refreshImgUrl();
                        });

                        refreshImgUrl();
                    }
                ]
        };
    }
);