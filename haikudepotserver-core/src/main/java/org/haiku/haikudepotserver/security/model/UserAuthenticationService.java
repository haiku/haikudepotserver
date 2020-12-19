/*
 * Copyright 2016-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.model;

import org.apache.cayenne.ObjectId;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.dataobjects.User;

import java.util.Optional;

/**
 * <p>This service is able to provide the ability to authenticate a user given their nickname and their clear-text
 * password.  It will maintain a cache of nickname to {@link ObjectId}s so that it is able to lookup users very quickly
 * if they are known to this instance.  This may be useful in a small-scale deployment.  This class is accessed by
 * the &quot;AuthenticationFilter&quot;.</p>
 */

public interface UserAuthenticationService {

    String CLAIM_REQUIRES_AGREE_USER_USAGE_CONDITIONS = "ucnd";

    Optional<ObjectId> authenticateByNicknameAndPassword(String nickname, String passwordClear);

    void setPassword(User user, String passwordClear);

    boolean matchPassword(User user, String passwordClear);

    /**
     * <p>Passwords should be hard to guess and so there needs to be a certain level of complexity to
     * them.  They should of a certain length and should contain some mix of letters and digits as well
     * as at least one upper case letter.</p>
     *
     * <p>This method will check the password for suitability.</p>
     */

    boolean validatePassword(String passwordClear);

    Optional<ObjectId> authenticateByToken(String payload);

    /**
     * <p>This will return a JWT (java web token) that is signed by a secret that allows for the client to get
     * that token and for it to be used as a form of authentication for some period of time.</p>
     */

    String generateToken(User user);

    /**
     * <p>This will extract the username, password couple that are transported in the Authorization header
     * when Basic authentication is used on an HTTP request.</p>
     */

    Optional<Pair<String, String>> tryExtractCredentialsFromBasicAuthorizationHeader(String header);

}
