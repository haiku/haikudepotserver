/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haiku.haikudepotserver.captcha.model.CaptchaRepository;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;

@ContextConfiguration(classes = TestConfig.class)
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
