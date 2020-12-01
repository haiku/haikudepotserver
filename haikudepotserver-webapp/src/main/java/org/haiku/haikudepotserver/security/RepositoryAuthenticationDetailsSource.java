/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;
import org.haiku.haikudepotserver.repository.controller.RepositoryController;
import org.haiku.haikudepotserver.security.model.RepositoryAuthenticationDetails;
import org.springframework.security.authentication.AuthenticationDetailsSource;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, RepositoryAuthenticationDetails> {

    private final static Pattern PATTERN_REPOSITORY_PREFIX =
            Pattern.compile("^/" + RepositoryController.SEGMENT_REPOSITORY
                    + "/(" + AbstractDataObject.CODE_PATTERN_STRING + ")/.+$");

    @Override
    public RepositoryAuthenticationDetails buildDetails(HttpServletRequest context) {
        Preconditions.checkArgument(null != context, "the http servlet request is required");
        Matcher matcher = PATTERN_REPOSITORY_PREFIX.matcher(StringUtils.trimToEmpty(context.getServletPath()));
        if (matcher.matches()) {
            return new RepositoryAuthenticationDetails(matcher.group(1));
        }
        return null;
    }
}
