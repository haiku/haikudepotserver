/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haiku.haikudepotserver.api2.CaptchaApiService;
import org.springframework.stereotype.Component;

@Deprecated
@Component("captchaApiImplV1")
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/captcha") // TODO; legacy path - remove
public class CaptchaApiImpl implements CaptchaApi {

    private final CaptchaApiService captchaApiService;

    public CaptchaApiImpl(CaptchaApiService captchaApiService) {
        this.captchaApiService = Preconditions.checkNotNull(captchaApiService);
    }

    @Override
    public GenerateCaptchaResult generateCaptcha(GenerateCaptchaRequest generateCaptchaRequest) {
        Preconditions.checkNotNull(generateCaptchaRequest);

        org.haiku.haikudepotserver.api2.model.GenerateCaptchaResult resultV2 = captchaApiService.generateCaptcha();

        GenerateCaptchaResult result = new GenerateCaptchaResult();
        result.token = resultV2.getToken();
        result.pngImageDataBase64 = resultV2.getPngImageDataBase64();
        return result;
    }
}
