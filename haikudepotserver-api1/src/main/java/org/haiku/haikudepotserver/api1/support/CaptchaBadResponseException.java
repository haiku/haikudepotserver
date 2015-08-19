/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

/**
 * <p>This exception is thrown in the case where the user has mis-entered a captcha.</p>
 */

public class CaptchaBadResponseException extends RuntimeException {

    public CaptchaBadResponseException() {
        super();
    }

}
