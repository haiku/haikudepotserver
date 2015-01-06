/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.haikuos.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haikuos.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haikuos.haikudepotserver.captcha.CaptchaService;
import org.haikuos.haikudepotserver.captcha.model.Captcha;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class CaptchaApiImpl implements CaptchaApi {

    @Resource
    private CaptchaService captchaService;

    @Override
    public GenerateCaptchaResult generateCaptcha(GenerateCaptchaRequest generateCaptchaRequest) {
        Preconditions.checkNotNull(generateCaptchaRequest);

        Captcha captcha = captchaService.generate();

        GenerateCaptchaResult result = new GenerateCaptchaResult();
        result.token = captcha.getToken();
        result.pngImageDataBase64 = BaseEncoding.base64().encode(captcha.getPngImageData());
        return result;
    }
}
