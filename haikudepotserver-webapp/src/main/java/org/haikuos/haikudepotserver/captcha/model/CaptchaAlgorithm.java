/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha.model;

/**
 * <p>This interface defines an object that is able to generate a new captcha.</p>
 */

public interface CaptchaAlgorithm {

    Captcha generate();

}
