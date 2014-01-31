/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

/**
 * <p>This service is here to maintain the current user's state.  When the user logs in for example, this is stored
 * here.  This service may take other actions such as configuring headers in the jsonRpc service when the user logs-in
 * or logs-out.</p>
 */

angular.module('haikudepotserver').factory('userState',
    [
        '$log','$q','jsonRpc','pkgIcon',
        function($log,$q,jsonRpc,pkgIcon) {

            var user = undefined;

            var UserState = {

                /**
                 * <p>Invoked with no argument, this function will return the user.  If it is supplied with null then
                 * it will set the current user to empty.  If it is supplied with a user value, it will configure the
                 * user.  The user should consist of the 'nickname' and the 'passwordClear'.</p>
                 */

                user : function(value) {
                    if(undefined !== value) {
                        if(null==value) {
                            user = undefined;

                            // remove the Authorization header for HTTP transport
                            jsonRpc.setHeader('Authorization');
                            pkgIcon.setHeader('Authorization');
                        }
                        else {

                            if(!value.nickname) {
                                throw 'the nickname is required when setting a user';
                            }

                            if(!value.passwordClear) {
                                throw 'the password clear is required when setting a user';
                            }

                            jsonRpc.setHeader(
                                'Authorization',
                                'Basic '+window.btoa(''+value.nickname+':'+value.passwordClear));

                            pkgIcon.setHeader(
                                'Authorization',
                                'Basic '+window.btoa(''+value.nickname+':'+value.passwordClear));

                            user = value;
                            $log.info('have set user; '+user.nickname);
                        }
                    }

                    return user;
                }

            };

            return UserState;

        }
    ]
);