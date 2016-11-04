/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.captcha.model;

import org.haiku.haikudepotserver.captcha.model.Captcha;

/**
 * <p>This service is able to provide interfacing to the captcha system including verification and generation of
 * captchas.</p>
 */

public interface CaptchaService {

    /**
     * <p>This method will generate a captcha, returning all of the details of the captcha.  Note that the captcha is
     * stored so that it can be validated within some time-frame.</p>
     */

    Captcha generate();

    /**
     * <p>This will check that the captcha identified by the supplied token has an expected response that matches the
     * response that is supplied to this method.  It will return true if this is the case.  Note that this method will
     * also delete the captcha such that it is not able to be verified again or re-used.</p>
     */

    boolean verify(String token, String response);

}
