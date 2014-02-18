/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This filter will render a human readable string for a data size.  It expects a value in bytes and it will
 * decide if it makes sense to render in MB or GB or...</p>
 */

angular.module('haikudepotserver').filter('dataLength', function() {
        return function(input) {

            if(undefined==input||null==input) {
                return '';
            }

            var val = !_.isNumber(input) ? input : parseInt(''+input,10);

            if(val < 1024) {
                return val + ' bytes';
            }

            if(val < 1024*1024) {
                return (val/1024).toFixed(1) + ' KB';
            }

            if(val < 1024*1024*1024) {
                return (val/(1024*1024)).toFixed(1) + ' MB';
            }

            return (val/(1024*1024*1024)).toFixed(1) + ' GB';
        }
    }
);