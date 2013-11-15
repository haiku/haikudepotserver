/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha.model;

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
        StringBuilder result = new StringBuilder();
        result.append(null!=token ? token.toString() : "???");
        result.append(" --> ");
        result.append(getResponse());
        result.append(" (");
        result.append(Integer.toString(pngImageData.length));
        result.append("b)");
        return result.toString();
    }

}
