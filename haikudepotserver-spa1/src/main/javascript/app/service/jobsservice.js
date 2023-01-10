/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service provides functionality for accessing and updating job data.</p>
 */

angular.module('haikudepotserver').factory('jobs',
    [
        '$log','$q','$http',
        function($log,$q,$http) {

            var errorCodes = {
                BADREQUEST : 400,
                UNKNOWN : -1
            };

            var headers = {};

            function setHeader(name, value) {
                if(!name || !name.length) {
                    throw Error('the name of the http header is required');
                }

                if(!value || !value.length) {
                    delete headers[name];
                }
                else {
                    headers[name] = value;
                }
            }

            /**
             * <p>Uploads the file so that it can be used as input for a job.  It will return a GUID reference to the
             * data if the upload was successful.</p>
             */

            function supplyData(file) {

                if(!file) {
                    throw Error('the file must be supplied to provide data for the upload.');
                }

                return $http({
                    cache: false,
                    method: 'POST',
                    url: '/__secured/jobdata',
                    headers: _.extend({ 'Content-Type' : 'application/octet-stream' },headers),
                    data: file
                }).then(
                    function successFunction(response) {
                        var code = response.headers('X-HaikuDepotServer-DataGuid');

                        if(!code || !code.length) {
                            throw Error('the data guid should have been supplied back from supplying data');
                        }

                        return code;
                    },
                    function failureFunction(response) {
                        switch(response.status) {
                            case 200: return $q.when();
                            case 400: return $q.reject(errorCodes.BADREQUEST);
                            default: return $q.reject(errorCodes.UNKNOWN);
                        }
                    }
                );
            }

            return {

                // these are errors that may be returned to the caller below.  They match to the HTTP status codes
                // used, but this should not be relied upon.

                errorCodes : errorCodes,

                setHeader : function(name, value) {
                    setHeader(name, value);
                },

                // this function will upload the data from the supplied file to the server and will return a GUID
                // that acts as a token to represent the data for subsequent API calls.  The returned data is
                // provided via a promise.

                supplyData : function(file) {
                    return supplyData(file);
                }

            };

        }
    ]
);