/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the screenshots for a package.</p>
 */

angular.module('haikudepotserver').factory('pkgScreenshot',
    [
        '$log','$q','$http',
        function($log,$q,$http) {

            var PkgScreenshot = {

                // these are errors that may be returned to the caller below.  They match to the HTTP status codes
                // used, but this should not be relied upon.

                errorCodes : {
                    BADFORMATORSIZEERROR : 415,
                    NOTFOUND : 404,
                    BADREQUEST : 400,
                    UNKNOWN : -1
                },

                /**
                 * <p>This is a map of HTTP headers that is sent on each request into the server.</p>
                 */

                headers : {},

                /**
                 * <p>This method will set the HTTP header that is sent on each request.  This is handy,
                 * for example for authentication.</p>
                 */

                setHeader : function(name, value) {

                    if(!name || 0==''+name.length) {
                        throw Error('the name of the http header is required');
                    }

                    if(!value || 0==''+value.length) {
                        delete PkgScreenshot.headers[name];
                    }
                    else {
                        PkgScreenshot.headers[name] = value;
                    }

                },

                // this function will add a screenshot for the package nominated.

                addScreenshot : function(pkg, screenshotFile) {

                    if(!pkg) {
                        throw Error('the pkg must be supplied to add a pkg screenshot');
                    }

                    if(!screenshotFile) {
                        throw Error('to add a screenshot for '+pkg.name+' the image file must be provided');
                    }

                    return $http({
                        cache: false,
                        method: 'POST',
                        url: '/pkgscreenshot/'+pkg.name+'/add?format=png',
                        headers: _.extend(
                            { 'Content-Type' : 'image/png' },
                            PkgScreenshot.headers),
                        data: screenshotFile
                    }).then(
                        function successFunction(response) {
                            var code = response.headers('X-HaikuDepotServer-ScreenshotCode');

                            if(!code || !code.length) {
                                throw Error('the screenshot code should have been supplied back from creating a new screenshot');
                            }

                            return code;
                        },
                        function failureFunction(response) {
                            switch(response.status) {
                                case 200: return $q.when();
                                case 415: return $q.reject(PkgScreenshot.errorCodes.BADFORMATORSIZEERROR); // unsupported media type
                                case 400: return $q.reject(PkgScreenshot.errorCodes.BADREQUEST);
                                case 404: return $q.reject(PkgScreenshot.errorCodes.NOTFOUND);
                                default: return $q.reject(PkgScreenshot.errorCodes.UNKNOWN);
                            }
                        }
                    );
                },

                /**
                 * <p>This function will provide a raw-data download for the screenshot.</p>
                 */

                rawUrl : function(pkg, code) {
                    if(!pkg) {
                        throw Error('the pkg must be supplied to get a package screenshot url');
                    }

                    if(!code || !code.length) {
                        throw Error('the code must be supplied to derive a url for the screenshot image');
                    }

                    return '/pkgscreenshot/' + code + '/raw';
                },

                /**
                 * <p>This function will provide a URL to the packages' screenshot.  The package object is supplied and
                 * a code is supplied to identify the actual screenshot.  The target width and height provide the
                 * size o the image that the screenshot will be scaled to.</p>
                 */

                url : function(pkg, code, targetWidth, targetHeight) {
                    if(!pkg) {
                        throw Error('the pkg must be supplied to get a package screenshot url');
                    }

                    if(!code || !code.length) {
                        throw Error('the code must be supplied to derive a url for the screenshot image');
                    }

                    var u = '/pkgscreenshot/' + code + '.png';
                    var q = [];

                    if(pkg.modifyTimestamp) {
                        q.push('m=' + pkg.modifyTimestamp);
                    }

                    if(targetWidth) {
                        q.push('tw=' + targetWidth);
                    }

                    if(targetHeight) {
                        q.push('th=' + targetHeight);
                    }

                    if(q.length) {
                        u += '?' + q.join('&');
                    }

                    return u;
                }

            };

            return PkgScreenshot;

        }
    ]
);