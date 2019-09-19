/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

public interface Constants {

    int ERROR_CODE_VALIDATION = -32800;
    int ERROR_CODE_OBJECTNOTFOUND = -32801;
    int ERROR_CODE_CAPTCHABADRESPONSE = -32802;
    int ERROR_CODE_AUTHORIZATIONFAILURE = -32803;
    int ERROR_CODE_BADPKGICON = -32804;
    @Deprecated
    int ERROR_CODE_LIMITEXCEEDED = -32805;
    int ERROR_CODE_AUTHORIZATIONRULECONFLICT = -32806;
    int ERROR_CODE_INVALID_USER_USAGE_CONDITIONS = -32810;
}
