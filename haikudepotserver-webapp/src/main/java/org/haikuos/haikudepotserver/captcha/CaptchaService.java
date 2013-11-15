/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.captcha.model.Captcha;
import org.haikuos.haikudepotserver.captcha.model.CaptchaAlgorithm;
import org.haikuos.haikudepotserver.captcha.model.CaptchaRepository;

/**
 * <p>This service is able to provide interfacing to the captcha system including verification and generation of
 * captchas.</p>
 */

public class CaptchaService {

    private CaptchaAlgorithm captchaAlgorithm;
    private CaptchaRepository captchaRepository;

    public CaptchaAlgorithm getCaptchaAlgorithm() {
        return captchaAlgorithm;
    }

    public void setCaptchaAlgorithm(CaptchaAlgorithm captchaAlgorithm) {
        this.captchaAlgorithm = captchaAlgorithm;
    }

    public CaptchaRepository getCaptchaRepository() {
        return captchaRepository;
    }

    public void setCaptchaRepository(CaptchaRepository captchaRepository) {
        this.captchaRepository = captchaRepository;
    }

    /**
     * <p>This method will generate a captcha, returning all of the details of the captcha.  Note that the captcha is
     * stored so that it can be validated within some time-frame.</p>
     * @return
     */

    public Captcha generate() {
        Captcha captcha = captchaAlgorithm.generate();
        captchaRepository.store(captcha.getToken(), captcha.getResponse());
        return captcha;
    }

    /**
     * <p>This will check that the captcha identified by the supplied token has an expected response that matches the
     * response that is supplied to this method.  It will return true if this is the case.  Note that this method will
     * also delete the captcha such that it is not able to be verified again or re-used.</p>
     */

    public boolean verify(String token, String response) {
        Preconditions.checkNotNull(token);

        // maybe better done less frequently?
        captchaRepository.purgeExpired();

        if(Strings.isNullOrEmpty(response)) {
            return false;
        }

        String databaseResponse = captchaRepository.get(token);

        if(null!=databaseResponse) {
            databaseResponse = databaseResponse.trim();
        }

        response.trim();

        if(null!=databaseResponse) {
            captchaRepository.delete(token);
            return response.equalsIgnoreCase(databaseResponse);
        }

        return false;
    }
}
