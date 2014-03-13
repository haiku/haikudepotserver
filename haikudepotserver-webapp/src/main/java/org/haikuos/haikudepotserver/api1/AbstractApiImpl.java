/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import org.haikuos.haikudepotserver.security.AbstractUserAuthenticationAware;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;


/**
 * <p>This abstract superclass of the API implementations allows access to the presently authenticated user.  See the
 * superclass for detail.</p>
 */

public abstract class AbstractApiImpl extends AbstractUserAuthenticationAware {

    @Autowired(required = false)
    private HttpServletRequest request;

    /**
     * <p>This method will try to obtain some sort of identifier for the current client; such as their IP address.</p>
     */

    protected String getRemoteIdentifier() {
        String result = null;

        if(null!=request) {
            result = request.getHeader(HttpHeaders.X_FORWARDED_FOR);

            if(!Strings.isNullOrEmpty(result)) {
                return result;
            }

            result = request.getRemoteAddr();

            if(!Strings.isNullOrEmpty(result)) {
                return result;
            }
        }

        return result;
    }

}
