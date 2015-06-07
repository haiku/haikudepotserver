/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is here to undertake textual manipulations that might be re-used in the application.</p>
 */

angular.module('haikudepotserver').factory('miscService',
    [
        // no injections
        function() {

            // --------------------------
            // ABBREVIATION

            function abbreviate(objs, fullPropertyName, abbreviatedPropertyName) {
                if(!objs) {
                    throw Error('an array of objects was expected for abbreviating');
                }

                if(!fullPropertyName||!fullPropertyName.length) {
                    throw Error('a full property name must be provided');
                }

                if(!abbreviatedPropertyName||!abbreviatedPropertyName.length) {
                    throw Error('a abbreviated property name must be provided');
                }

                // TODO - this is too simplistic; try something better later where it avoid dups etc..

                function abbreviateUniqueStrings(strs) {

                    if(!strs) {
                        throw Error('the string should be provided to abbreviate.');
                    }

                    return _.map(strs, function(s) {
                        return _.map(
                            _.filter(
                                s.split(/[\s]+/),
                                function(s2) {
                                    return !!s2.length;
                                }
                            ),
                            function(s2) {
                                if(s2.length > 2) {
                                    return s2.substring(0,2)+'.';
                                }

                                return s2;
                            }
                        ).join(' ');
                    });

                }

                switch(objs.length) {

                    case 0: // nothing to do
                        break;

                    case 1: // nothing to conflict with
                        objs[0][abbreviatedPropertyName] = !!objs[0][fullPropertyName] ? objs[0][fullPropertyName] : '';
                        break;

                    default:

                        var fulls = _.uniq(_.map(
                            objs,
                            function(o) {
                                return !!o[fullPropertyName] ? o[fullPropertyName] : '';
                            }
                        ));

                        var abbreviations = abbreviateUniqueStrings(fulls);

                        _.each(objs, function(o) {
                            var full = !!o[fullPropertyName] ? o[fullPropertyName] : '';
                           var idx = _.indexOf(fulls, full);

                            if(idx < 0) {
                                throw Error('illegal state; unable to find the original full value');
                            }

                            o[abbreviatedPropertyName] = abbreviations[idx];
                        });

                        break;
                }

                return objs;
            }

            // --------------------------
            // BASE 64 URL

            function stripBase64FromDataUrl(url) {

                if(!url||!url.length) {
                    return undefined;
                }

                /**
                 * This is a recursive function that consumes the start of the data url in order to discover
                 * the base64 data string.
                 * @param {string} u
                 * @param {number} offset
                 */

                function indexToStartOfBase64(u,offset) {

                    if(!u||!u.length) {
                        throw Error('the data url must be supplied to convert to base64');
                    }

                    if(offset >= u.length) {
                        throw Error('unexpected end of data url');
                    }

                    if(0==offset && 0 == u.indexOf('data:')) {
                        return indexToStartOfBase64(u,5);
                    }

                    if(offset == u.indexOf('base64,',offset)) {
                        return offset + 7;
                    }

                    var semicolonI = u.indexOf(';',offset);

                    if(-1==semicolonI) {

                        var uTrimmed = u;

                        if(uTrimmed.length > 32) {
                            uTrimmed = uTrimmed.substring(0,28) + '...';
                        }

                        throw Error('unexpected end of data url; ' + uTrimmed);
                    }

                    return indexToStartOfBase64(u,semicolonI+1);
                }

                return url.substring(indexToStartOfBase64(url,0));

            }

            // --------------------------
            // PUBLIC API

            return {

                /**
                 * Takes a string of the form; "data:[<MIME-type>][;charset=<encoding>][;base64],<data>"
                 * and strips from it the base64 portion.  If the url is empty then undefined will be
                 * returned.
                 * @param {string} url
                 */

                stripBase64FromDataUrl : function(url) {
                    return stripBase64FromDataUrl(url);
                },

                /**
                 * Takes an array of objects and two keys.  It will then try to produce abbreviated
                 * names for the full values so that they can be somewhat distinguished from the
                 * other strings.
                 * @param objs is an array of objects that need to be abbreviated
                 * @param fullPropertyName allows the full length string to be obtained from each object.
                 * @param abbreviatedPropertyName is the property name where the abbreviated value will be written back into the object.
                 * @returns the list of objects supplied.
                 */

                abbreviate : function(objs, fullPropertyName, abbreviatedPropertyName) {
                    return abbreviate(objs, fullPropertyName, abbreviatedPropertyName);
                }

            };

        }
    ]
);