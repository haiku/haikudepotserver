/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.CaptchaApi;
import org.haikuos.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haikuos.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haikuos.haikudepotserver.captcha.model.CaptchaRepository;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.junit.Test;

import javax.annotation.Resource;

public class CaptchaApiIT extends AbstractIntegrationTest {

    @Resource
    CaptchaApi captchaApi;

    @Resource
    CaptchaRepository captchaRepository;

    /**
     * <p>This is a bit of a limited test because there is nothing to actually check without having a person
     * to interpret the image.  In any case, it will provide for an element of just exercising the API to make
     * sure that it at least does not fail.</p>
     */

    @Test
    public void testGenerateCaptcha() {

        // ------------------------------------

        GenerateCaptchaResult result = captchaApi.generateCaptcha(new GenerateCaptchaRequest());

        // ------------------------------------

        Assertions.assertThat(result.token).isNotEmpty();
        Assertions.assertThat(captchaRepository.get(result.token)).isNotNull();

    }

}
