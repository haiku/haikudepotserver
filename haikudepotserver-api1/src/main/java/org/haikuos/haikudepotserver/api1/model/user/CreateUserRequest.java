/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.user;

public class CreateUserRequest {

    public String nickname;
    public String passwordClear;
    public String email;

    /**
     * <p>The captcha token is obtained from an earlier invocation to the
     * {@link org.haikuos.haikudepotserver.api1.CaptchaApi} method to get
     * a captcha.  This identifies the captcha for which the captcha response should
     * correlate.</p>
     */

    public String captchaToken;

    /**
     * <p>This is the human-supplied text string that matches the image that would have been
     * provided with the captcha that is identified by the cpatchaToken.</p>
     */

    public String captchaResponse;

    /**
     * <p>This code comes from the {@link org.haikuos.haikudepotserver.api1.model.miscellaneous.GetAllNaturalLanguagesResult.NaturalLanguage}
     * entity.</p>
     */

    public String naturalLanguageCode;

}
