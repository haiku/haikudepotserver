/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

// WebPositive, the browser for HaikuOS informs the Modernizr that it is able to handle SVG,
// but it is not able to.  Detect WebPositive and force Modernizr to not support SVG.

(function() {
    if (window.navigator && window.navigator.userAgent && / WebPositive\/\d/.test(window.navigator.userAgent)) {
        Modernizr.svg = false;
        if (window.console) {
            console.log('did detect WebPositive; will disable SVG in Modernizr');
        }
    }
})();