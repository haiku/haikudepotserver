/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.captcha.model;

/**
 * <p>This interface defines a class that is able to store expected responses against captcha tokens.</p>
 */

public interface CaptchaRepository {

    /**
     * <p>This method will remove those captchas that have expired.</p>
     */

    void purgeExpired();

    /**
     * <p>This method will delete the captcha identified by the UUID supplied.</p>
     */

    boolean delete(String token);

    /**
     * <p>This method will obtain the response for the captcha identified by the UUID.  This method will return
     * a null value if the token was not able to be looked up against the repository of captchas.
     */

    String get(String token);

    /**
     * <p>This method will store a new token in the database.</p>
     */

    void store(String token, String response);

}
