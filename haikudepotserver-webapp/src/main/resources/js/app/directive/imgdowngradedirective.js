/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This directive is able to detect that it has a "src" attribute that points to an SVG.  If it detects
 * that the browser is not able to handle SVG then it swaps the SVG for a PNG.  Optionally the PNG may be
 * supplied as the value for this attribute.</p>
 */

angular.module('haikudepotserver').directive('imgDowngrade',[
        function() {
            return {
                restrict: 'A',
                link: function ($scope, element, attributes) {

                    var src = element.attr('src');

                    if (src && /\.svg$/.test(src) && !Modernizr.svg) {
                        var alternativeSrc = attributes['imgDowngrade'];

                        if (alternativeSrc && alternativeSrc.length) {
                            element.attr('src', alternativeSrc);
                        }
                        else {
                            element.attr('src', src.substring(0, src.length - 4) + '.png');
                        }
                    }
                }

            }
        }]
);