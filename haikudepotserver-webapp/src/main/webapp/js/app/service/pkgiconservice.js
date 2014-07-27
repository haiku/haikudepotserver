/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the icons for a package.</p>
 */

angular.module('haikudepotserver').factory('pkgIcon',
    [
        '$log','$q','$http','constants',
        function($log,$q,$http,constants) {

            return {

                /**
                 * <p>This function will provide a URL to the packages' icon.</p>
                 */

                url : function(pkg, mediaTypeCode, size) {

                    if(!pkg) {
                        throw Error('the pkg must be supplied to get the package icon url');
                    }

                    var u = '/pkgicon/' + pkg.name;

                    if(!mediaTypeCode) {
                        throw Error('the media type code is required to get the package icon url');
                    }

                    switch(mediaTypeCode) {
                        case constants.MEDIATYPE_HAIKUVECTORICONFILE:
                            u += '.hvif';
                            break;

                        case constants.MEDIATYPE_PNG: {
                            if(!size || !(32==size||16==size)) {
                                throw Error('the size is not valid for obtaining the package icon url');
                            }

                            u += '.png?f=true&s=' + size
                        }
                            break;

                        default:
                            throw Error('unknown media type; ' + mediaTypeCode);
                    }

                    if(pkg.modifyTimestamp) {
                        u += -1==u.indexOf('?') ? '?' : '&';
                        u += 'm=' + pkg.modifyTimestamp;
                    }

                    return u;
                }

            };

        }
    ]
);