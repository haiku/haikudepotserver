/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').constant('constants', {

        ARCHITECTURE_CODE_DEFAULT : 'x86',

        ENDPOINT_API_V1_PKG : '/api/v1/pkg',
        ENDPOINT_API_V1_CAPTCHA : '/api/v1/captcha',
        ENDPOINT_API_V1_MISCELLANEOUS : '/api/v1/miscellaneous',
        ENDPOINT_API_V1_USER : '/api/v1/user',

        /**
         * <p>This function expects to be supplied a JSON-RPC error object and will then direct the user to
         * an error page from where they can return into the application again.</p>
         */

        ERRORHANDLING_JSONRPC : function(err,$location,$log) {
            if($log) {
                $log.error('an error has arisen in invoking a json-rpc method');
            }

            $location.path("/error").search({});
        }
    }
);