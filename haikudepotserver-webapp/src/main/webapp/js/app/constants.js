/*
 * Copyright 2013-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

angular.module('haikudepotserver')
    .constant('constants', {

        STORAGE_TOKEN_KEY : 'hds.userstate.token',
        STORAGE_PREFERENTIAL_REPOSITORY_CODES_KEY : 'hds.repo.prefcodes',

        NATURALLANGUAGECODE_ENGLISH : 'en',

        DELAY_SPINNER : 1000, // millis

        // used for search constraint on the 'home' page.

        RECENT_DAYS : 90,

        ENDPOINT_API_V1_REPOSITORY : '/__api/v1/repository',
        ENDPOINT_API_V1_PKG : '/__api/v1/pkg',
        ENDPOINT_API_V1_PKG_JOB: '/__api/v1/pkg/job',
        ENDPOINT_API_V1_CAPTCHA : '/__api/v1/captcha',
        ENDPOINT_API_V1_MISCELLANEOUS : '/__api/v1/miscellaneous',
        ENDPOINT_API_V1_USER : '/__api/v1/user',
        ENDPOINT_API_V1_JOB : '/__api/v1/job',
        ENDPOINT_API_V1_USERRATING : '/__api/v1/userrating',
        ENDPOINT_API_V1_USERRATING_JOB : '/__api/v1/userrating/job',
        ENDPOINT_API_V1_AUTHORIZATION : '/__api/v1/authorization',
        ENDPOINT_API_V1_AUTHORIZATION_JOB : '/__api/v1/authorization/job',

        MEDIATYPE_PNG : 'image/png',
        MEDIATYPE_HAIKUVECTORICONFILE : 'application/x-vnd.haiku-icon',

        PATTERN_USER_NICKNAME : /^[a-z0-9]{4,16}$/,
        PATTERN_PKG_NAME : /^[^\s/=!<>-]+$/,

        KEY_NATURALLANGUAGECODE : 'natlangcode',

        SUFFIXES_SUBORDINATE_PKG_NAMES : [ '_devel', '_sources' ]

})

    // this constant provides mixins that are related somehow to search.

    .constant('searchMixins', {

        /**
         * <p>Often, when searching, it is necessary to use a search expression to find something.  This function
         * will take a candidate string and will find the next occurrence of the search expression after the
         * index.  Search expressions come in different types such as "CONTAINS".  If there is no next match then
         * the function will return a tuple { offset : -1, length : -1 }.  If there is a next match then the
         * function will return a tuple containing the offset to the next match as well as the length of the next
         * match.</p>
         *
         * <p>note that both the string and the searchExpression should be supplied as lower case.</p>
         */

        nextMatchSearchExpression: function(str, offset, searchExpression, searchExpressionType) {

            if(!searchExpressionType||!searchExpressionType.length) {
                throw Error('the search expression type must be supplied');
            }

            if(null==offset || offset < 0) {
                throw Error('an offset is required');
            }

            if(str&&
                str.length&&
                searchExpression&&
                searchExpression.length&&
                offset < str.length) {

                switch(searchExpressionType) {
                    case 'CONTAINS':
                        var found = str.indexOf(searchExpression, offset);

                        if(-1!=found) {
                            return {
                                offset: found,
                                length: searchExpression.length
                            };
                        }
                        break;

                    default:
                        throw Error('unknown search expression type; ' + searchExpressionType);
                }

            }

            return {
                offset: -1,
                length: -1
            };
        }

    })

    // this constant is an object of mix-ins that can be used to extend a directive so that it
    // has access to a cache of handy functions that can be re-used.

    .constant('standardDirectiveFunctions', {

        /**
         * <p>This function is able to adjust the visibility of the element by adding or removing a class.  It
         * will hide or show the element based on the results of a permissions check.  Either a single
         * permissions can be supplied to check or the an array of permission codes.  If the target or the
         * permissions are empty then the element will not be shown.</p>
         *
         * <p>Note that a null value for the targetIdentifier and the targetType means to evaluate the permissions
         * against the currently authenticated principal.</p>
         *
         * @param permissionCode is either an array or a single permission represented as a string.
         * @param targetType is the type of the target object against which the permission check is undertaken; eg "REPOSITORY"
         * @param targetIdentifier is the identifier for the target; eg "erik" for a user
         */

        showOrHideElementAfterCheckPermission : function(
            userState,
            element,
            permissionCode,
            targetType,
            targetIdentifier) {

            if(!targetType && targetIdentifier) {
                throw Error('if the target type is null (check on principal) then the target identifier is also expected to be null');
            }

            if(!permissionCode || (!targetIdentifier && !!targetType)) {
                element.addClass('app-hide');
            }
            else {
                var targetAndPermissions = [];

                if(angular.isArray(permissionCode)) {
                    _.each(permissionCode, function(item) {
                        targetAndPermissions.push({
                            targetType: targetType,
                            targetIdentifier : targetIdentifier,
                            permissionCode : '' + item
                        });
                    });
                }
                else {
                    targetAndPermissions.push({
                        targetType: targetType,
                        targetIdentifier : targetIdentifier,
                        permissionCode : '' + permissionCode
                    });
                }

                userState.areAuthorized(targetAndPermissions).then(function(flag) {
                    if(flag) {
                        element.removeClass('app-hide');
                    }
                    else {
                        element.addClass('app-hide');
                    }
                });
            }

        },

        /**
         * <p>This function will return true if the element supplied appears to be inside
         * a form element.</p>
         */

        isChildOfForm: function (e) {

            function isChildOfFormInternal(e2) {
                var tagName = e2.prop('tagName').toLowerCase();

                switch (tagName) {

                    case 'form':
                        return true;

                    case 'html':
                    case 'body':
                        break;

                    default:
                        var p2 = e2.parent();
                        if (p2 && p2.length) {
                            return isChildOfFormInternal(p2);
                        }
                        break;
                }

                return false;
            }

            return isChildOfFormInternal(e);
        },

        /**
         * <p>This function will return a string that represents the version number in a string form.  The
         * logic for this should be the same as the logic in the C/C++ code in "PackageVersion.cpp".</p>
         */

        pkgVersionElementsToString: function(pkgVersion) {
            var result = '' + pkgVersion.major;

            if(pkgVersion.minor && '' != pkgVersion.minor) {
                result += '.';
                result += pkgVersion.minor;

                if(pkgVersion.micro && '' != pkgVersion.micro) {
                    result += '.';
                    result += pkgVersion.micro;
                }
            }

            if(pkgVersion.preRelease && '' != pkgVersion.preRelease) {
                result += '~';
                result += pkgVersion.preRelease
            }

            if(pkgVersion.revision && pkgVersion.revision > 0) {
                result += '-';
                result += pkgVersion.revision
            }

            return result;
        }
    });