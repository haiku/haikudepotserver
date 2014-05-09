/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver').constant('constants', {

        NATURALLANGUAGECODE_ENGLISH : 'en',

        // used for search constraint on the 'home' page.

        RECENT_DAYS : 90,

        ARCHITECTURE_CODE_DEFAULT : 'x86',

        ENDPOINT_API_V1_REPOSITORY : '/api/v1/repository',
        ENDPOINT_API_V1_PKG : '/api/v1/pkg',
        ENDPOINT_API_V1_CAPTCHA : '/api/v1/captcha',
        ENDPOINT_API_V1_MISCELLANEOUS : '/api/v1/miscellaneous',
        ENDPOINT_API_V1_USER : '/api/v1/user',

        MEDIATYPE_PNG : 'image/png',
        MEDIATYPE_HAIKUVECTORICONFILE : 'application/x-vnd.haiku-icon',

        SVG_RIGHT_ARROW : '<svg height=\"12\" width=\"12\"><path fill=\"black\" d=\"M0 4.5 L0 7.5 L4 7.5 L4 12 L12 6 L4 0 L4 4.5\"/></svg>',
        SVG_LEFT_ARROW : '<svg height=\"12\" width=\"12\"><path fill=\"black\" d=\"M12 4.5 L12 7.5 L8 7.5 L8 12 L0 6 L8 0 L8 4.5\"/></svg>'
    }
);