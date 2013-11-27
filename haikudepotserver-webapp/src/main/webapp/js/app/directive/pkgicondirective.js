/*
 * Copyright 2013, Andrew Lindesay
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
            template:'<img src="{{imgUrl}}"></img>',
            replace: true,
            scope: {
                size:'@',
                pkg:'='
            },
            controller:
                ['$scope','$log','messageSource',
                    function($scope,$log,messageSource) {

                        $scope.imgUrl = undefined;

                        function refreshImgUrl() {
                            if($scope.size && $scope.pkg) {
                                if(!$scope.pkg.name) {
                                    throw 'pkg does not contain a name to identify the pkg for the pkg-icon';
                                }
                                else {
                                    $scope.imgUrl = '/pkgicon/' + $scope.pkg.name + '.png?s=' + $scope.size;
                                }
                            }
                            else {
                                $scope.imgUrl = '';
                            }
                        }

                        $scope.$watch('size',function() {
                            refreshImgUrl();
                        });

                        $scope.$watch('pkg',function() {
                            refreshImgUrl();
                        });

                        refreshImgUrl();
                    }
                ]
        };
    }
);