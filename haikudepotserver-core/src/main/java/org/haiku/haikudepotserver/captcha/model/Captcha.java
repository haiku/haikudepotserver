/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.captcha.model;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * <p>This object models a captcha; which is an image that contains instructions for a human to convert into text to
 * confirm that they are more likely to be a human.</p>
 */

public class Captcha {

    /**
     * <p>This string uniquely identifies the captcha.  This is generally the mechanism by which the captcha is
     * referenced in other API and services.</p>
     */

    private String token;

    /**
     * <p>This is the expected response that a human operator should be expected to provide.</p>
     */

    private String response;

    /**
     * <p>This is a PNG image that contains the instruction for the user to convert into some text in their
     * response.</p>
     */

    private byte[] pngImageData;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public byte[] getPngImageData() {
        return pngImageData;
    }

    public void setPngImageData(byte[] pngImageData) {
        this.pngImageData = pngImageData;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("token", token)
                .append("pngImageData.length", pngImageData.length)
                .build();
    }

}
