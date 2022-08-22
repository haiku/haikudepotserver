/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>The single-page Thymeleaf page has an 'unsupported' include.  This will show a warning if the browser is not
 * able to support this application because, for example, it is too old or has javascript disabled.  This
 * directive will remove this so the application displays properly.</p>
 */

angular.module('haikudepotserver').directive('supported',[
    // no injections
    function() {
        return {
            restrict: 'E',
            link : function($scope,element,attributes) {

                var d = element[0].ownerDocument;
                var unsupportedEl = d.getElementById('unsupported');

                if(!unsupportedEl) {
                    throw new Error('unable to find the "unsupported" element');
                }

                var unsupportedElClass = unsupportedEl.getAttribute('class');
                var unsupportedElClasses = [];

                if(unsupportedElClass && unsupportedElClass.length) {
                    unsupportedElClasses = unsupportedElClass.split(' ');
                }

                unsupportedElClasses.push('unsupported-hide');
                unsupportedEl.setAttribute('class',unsupportedElClasses.join(' '));

            }
        }

    }
]);
