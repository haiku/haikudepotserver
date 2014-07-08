/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset.model;

/**
 * <p>Data model able to be used to fill the freemarker template.</p>
 */

public class PasswordResetMail {

    private String userNickname;
    private String passwordResetBaseUrl;
    private String userPasswordResetTokenCode;

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getPasswordResetBaseUrl() {
        return passwordResetBaseUrl;
    }

    public void setPasswordResetBaseUrl(String passwordResetBaseUrl) {
        this.passwordResetBaseUrl = passwordResetBaseUrl;
    }

    public String getUserPasswordResetTokenCode() {
        return userPasswordResetTokenCode;
    }

    public void setUserPasswordResetTokenCode(String userPasswordResetTokenCode) {
        this.userPasswordResetTokenCode = userPasswordResetTokenCode;
    }
}
