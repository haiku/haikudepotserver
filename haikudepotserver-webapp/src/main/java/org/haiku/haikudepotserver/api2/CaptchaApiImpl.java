/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haiku.haikudepotserver.api2.model.GenerateCaptureResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class CaptchaApiImpl extends AbstractApiImpl implements CaptchaApi {

    private final org.haiku.haikudepotserver.api1.CaptchaApiImpl captchaApiV1;

    public CaptchaApiImpl(org.haiku.haikudepotserver.api1.CaptchaApiImpl captchaApiV1) {
        this.captchaApiV1 = captchaApiV1;
    }

    @Override
    public ResponseEntity<GenerateCaptureResponseEnvelope> generateCaptcha(Object body) {
        GenerateCaptchaResult resultV1 = captchaApiV1.generateCaptcha(new GenerateCaptchaRequest());
        org.haiku.haikudepotserver.api2.model.GenerateCaptchaResult resultV2 = new org.haiku.haikudepotserver.api2.model.GenerateCaptchaResult();
        resultV2.setToken(resultV1.token);
        resultV2.setPngImageDataBase64(resultV1.pngImageDataBase64);
        return ResponseEntity.ok(new GenerateCaptureResponseEnvelope().result(resultV2));
    }

}
