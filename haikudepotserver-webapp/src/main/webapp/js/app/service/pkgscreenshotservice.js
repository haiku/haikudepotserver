/*
 * Copyright 2014, Andrew Lindesay
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
                        throw 'the name of the http header is required';
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
                        throw 'the pkg must be supplied to add a pkg screenshot';
                    }

                    if(!screenshotFile) {
                        throw 'to add a screenshot for '+pkg.name+' the image file must be provided';
                    }

                    var deferred = $q.defer();

                    $http({
                        cache: false,
                        method: 'PUT',
                        url: '/pkgscreenshot/'+pkg.name+'.png',
                        headers: _.extend(
                            { 'Content-Type' : 'image/png' },
                            PkgScreenshot.headers),
                        data: screenshotFile
                    })
                        .success(function(data,status,header,config) {
                            var code = header('X-HaikuDepotServer-ScreenshotCode');

                            if(!code || !code.length) {
                                throw 'the screenshot code should have been supplied back from creating a new screenshot';
                            }

                            deferred.resolve(code);
                        })
                        .error(function(data,status,header,config) {
                            switch(status) {
                                case 200:
                                    deferred.resolve();
                                    break;

                                case 415: // unsupported media type
                                    deferred.reject(PkgScreenshot.errorCodes.BADFORMATORSIZEERROR);
                                    break;

                                case 400: // bad request
                                    deferred.reject(PkgScreenshot.errorCodes.BADREQUEST);
                                    break;

                                case 404: // not found
                                    deferred.reject(PkgScreenshot.errorCodes.NOTFOUND);
                                    break;

                                default:
                                    deferred.reject(PkgScreenshot.errorCodes.UNKNOWN);
                                    break;

                            }
                        });

                    return deferred.promise;
                },

                /**
                 * <p>This function will provide a raw-data download for the screenshot.</p>
                 */

                rawUrl : function(pkg, code) {
                    if(!pkg) {
                        throw 'the pkg must be supplied to get a package screenshot url';
                    }

                    if(!code || !code.length) {
                        throw 'the code must be supplied to derive a url for the screenshot image';
                    }

                    return '/pkgscreenshot/raw/' + code;
                },

                /**
                 * <p>This function will provide a URL to the packages' screenshot.  The package object is supplied and
                 * a code is supplied to identify the actual screenshot.  The target width and height provide the
                 * size o the image that the screenshot will be scaled to.</p>
                 */

                url : function(pkg, code, targetWidth, targetHeight) {
                    if(!pkg) {
                        throw 'the pkg must be supplied to get a package screenshot url';
                    }

                    if(!code || !code.length) {
                        throw 'the code must be supplied to derive a url for the screenshot image';
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