/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaRequest;
import org.haiku.haikudepotserver.api1.model.captcha.GenerateCaptchaResult;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.springframework.stereotype.Component;

@Component("captchaApiImplV1")
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/captcha") // TODO; legacy path - remove
public class CaptchaApiImpl implements CaptchaApi {

    private final CaptchaService captchaService;

    public CaptchaApiImpl(CaptchaService captchaService) {
        this.captchaService = Preconditions.checkNotNull(captchaService);
    }

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
