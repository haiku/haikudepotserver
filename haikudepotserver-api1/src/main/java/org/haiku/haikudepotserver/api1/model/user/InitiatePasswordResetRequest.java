/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

import org.haiku.haikudepotserver.api1.CaptchaApi;

public class InitiatePasswordResetRequest {

    public String email;

    /**
     * <p>The captcha token is obtained from an earlier invocation to the
     * {@link CaptchaApi} method to get
     * a captcha.  This identifies the captcha for which the captcha response should
     * correlate.</p>
     */

    public String captchaToken;

    /**
     * <p>This is the human-supplied text string that matches the image that would have been
     * provided with the captcha that is identified by the cpatchaToken.</p>
     */

    public String captchaResponse;

}
