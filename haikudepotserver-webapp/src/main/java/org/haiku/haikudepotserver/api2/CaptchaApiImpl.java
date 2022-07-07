/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api2.model.GenerateCaptureResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class CaptchaApiImpl implements CaptchaApi {

    private final CaptchaApiService captchaApiService;

    public CaptchaApiImpl(CaptchaApiService captchaApiService) {
        this.captchaApiService = captchaApiService;
    }

    @Override
    public ResponseEntity<GenerateCaptureResponseEnvelope> generateCaptcha(Object body) {
        return ResponseEntity.ok(
                new GenerateCaptureResponseEnvelope()
                        .result(captchaApiService.generateCaptcha()));
    }

}
