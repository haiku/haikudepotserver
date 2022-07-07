/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.haiku.haikudepotserver.api2.model.GenerateCaptchaResult;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.springframework.stereotype.Component;

@Component("captchaApiServiceV2")
public class CaptchaApiService extends AbstractApiService {

    private final CaptchaService captchaService;

    public CaptchaApiService(CaptchaService captchaService) {
        this.captchaService = Preconditions.checkNotNull(captchaService);
    }

    public GenerateCaptchaResult generateCaptcha() {
        Captcha captcha = captchaService.generate();
        return new GenerateCaptchaResult()
                .token(captcha.getToken())
                .pngImageDataBase64(BaseEncoding.base64().encode(captcha.getPngImageData()));
    }

}
