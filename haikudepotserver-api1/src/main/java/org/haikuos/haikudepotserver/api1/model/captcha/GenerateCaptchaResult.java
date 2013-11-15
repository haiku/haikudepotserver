/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.captcha;

public class GenerateCaptchaResult {

    /**
     * <p>This token uniquely identifies the captcha.</p>
     */

    public String token;

    /**
     * <p>This is a base-64 encoded image of the captcha.  It could, for example, be used with a data url to render
     * the image in an "img" tag on a web page.</p>
     */

    public String pngImageDataBase64;

}
