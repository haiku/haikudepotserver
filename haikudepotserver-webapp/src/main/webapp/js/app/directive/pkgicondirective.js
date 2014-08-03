/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to render an icon for the pkg bound to it.  It expects that the size is also provided.
 * There may be restrictions on the size at the server end where the pkg icon image is ultimately delivered.  The
 * 'pkg' data structure is expected to have a 'name' property
 */

angular.module('haikudepotserver').directive('pkgIcon',
    ['pkgIcon','constants',
        function(pkgIcon,constants) {
            return {
                restrict: 'E',
                link: function ($scope, element, attributes) {

                    var size = attributes['size'];
                    var pkgExpression = attributes['pkg'];

                    if (!pkgExpression || !pkgExpression.length) {
                        throw Error('the pkg binding must be an expression to a package');
                    }

                    if (!size) {
                        throw Error('the size binding must be supplied');
                    }

                    size = parseInt('' + size, 10);

                    if (size < 1 || size > 1000) {
                        throw Error('preposterous value for size; ' + size);
                    }

                    var el = angular.element('<img src="" width="' + size + '" height="' + size + '"></img>');
                    element.replaceWith(el);

                    $scope.$watch(pkgExpression, function(pkg) {
                        var url = '/img/generic'+size+'.png';

                        if (pkg) {
                            if (!pkg.name||!pkg.name.length) {
                                throw Error('the package is expected to have a name in order to derive an icon url');
                            }

                            if(undefined == pkg.hasAnyPkgIcons || pkg.hasAnyPkgIcons) {
                                url = pkgIcon.url(pkg, constants.MEDIATYPE_PNG, size);
                            }
                        }

                        el.attr('src', url);
                    });
                }
            }
        }
    ]
);