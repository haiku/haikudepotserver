/*
 * Copyright 2016-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public class HttpRequestClientIdentifierSupplier implements ClientIdentifierSupplier {

    @Autowired(required = false)
    private HttpServletRequest request;

    @Override
    public Optional<String> get() {
        if(null!=request) {
            String result = request.getHeader(HttpHeaders.X_FORWARDED_FOR);

            if (!Strings.isNullOrEmpty(result)) {
                return Optional.of(result);
            }

            result = request.getRemoteAddr();

            if (!Strings.isNullOrEmpty(result)) {
                return Optional.of(result);
            }
        }

        return Optional.empty();
    }
}
