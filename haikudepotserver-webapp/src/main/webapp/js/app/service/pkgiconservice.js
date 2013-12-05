/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating the icons for a package.</p>
 */

angular.module('haikudepotserver').factory('pkgIcon',
    [
        '$log','$q','$http',
        function($log,$q,$http) {

            var PkgIcon = {

                // these are errors that may be returned to the caller below.  They match to the HTTP status codes
                // used, but this should not be relied upon.

                errorCodes : {
                    BADFORMATORSIZEERROR : 415,
                    NOTFOUND : 404,
                    BADREQUEST : 404,
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
                        delete PkgIcon.headers[name];
                    }
                    else {
                        PkgIcon.headers[name] = value;
                    }

                },

                // this function will set the icon for the package.  Note that there may be more than one variant and
                // this method will need to be invoked for each variant; or example 16px or 32px versions of the same
                // package icon.

                setPkgIcon : function(pkg, iconFile, expectedSize) {

                    if(!pkg) {
                        throw 'the pkg must be supplied to set the pkg icon';
                    }

                    if(!iconFile) {
                        throw 'to set the pkg icon for '+pkg.name+' the image file must be provided';
                    }

                    if(!expectedSize || !(16==expectedSize || 32==expectedSize)) {
                        throw 'the expected size must be 16 or 32px';
                    }

                    var deferred = $q.defer();

                    $http({
                        cache: false,
                        method: 'PUT',
                        url: '/pkgicon/'+pkg.name+'.png?s='+expectedSize,
                        headers: _.extend(
                            { 'Content-Type' : 'image/png' },
                            PkgIcon.headers),
                        data: iconFile
                    })
                        .success(function(data,status,header,config) {
                            deferred.resolve();
                        })
                        .error(function(data,status,header,config) {
                            switch(status) {
                                case 200:
                                    deferred.resolve();
                                    break;

                                case 415: // unsupported media type
                                    deferred.reject(PkgIcon.errorCodes.BADFORMATORSIZEERROR);
                                    break;

                                case 400: // bad request
                                    deferred.reject(PkgIcon.errorCodes.BADREQUEST);
                                    break;

                                case 404: // not found
                                    deferred.reject(PkgIcon.errorCodes.NOTFOUND);
                                    break;

                                default:
                                    deferred.reject(PkgIcon.errorCodes.UNKNOWN);
                                    break;

                            }
                        });

                    return deferred.promise;
                },

                /**
                 * <p>This function will provide a URL to the packages' icon.</p>
                 */

                url : function(pkg, size) {
                    if(!pkg) {
                        throw 'the pkg must be supplied to get the package icon url';
                    }

                    if(!size || !(32==size||16==size)) {
                        throw 'the size is not valid for obtaining the package icon url';
                    }

                    var u = '/pkgicon/' + pkg.name + '.png?s=' + size;

                    if(pkg.modifyTimestamp) {
                        u += '&m=' + pkg.modifyTimestamp;
                    }

                    return u;
                }

            };

            return PkgIcon;

        }
    ]
);