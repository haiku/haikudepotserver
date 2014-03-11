/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').constant('constants', {

        // used for search constraint on the 'home' page.

        RECENT_DAYS : 90,

        ARCHITECTURE_CODE_DEFAULT : 'x86',

        ENDPOINT_API_V1_REPOSITORY : '/api/v1/repository',
        ENDPOINT_API_V1_PKG : '/api/v1/pkg',
        ENDPOINT_API_V1_CAPTCHA : '/api/v1/captcha',
        ENDPOINT_API_V1_MISCELLANEOUS : '/api/v1/miscellaneous',
        ENDPOINT_API_V1_USER : '/api/v1/user',

        MEDIATYPE_PNG : 'image/png',
        MEDIATYPE_HAIKUVECTORICONFILE : 'application/x-vnd.haiku-icon'
    }
);