package org.haiku.haikudepotserver.captcha;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaAlgorithm;
import org.haiku.haikudepotserver.captcha.model.CaptchaRepository;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;

public class CaptchaServiceImpl implements CaptchaService {

    private CaptchaAlgorithm captchaAlgorithm;
    private CaptchaRepository captchaRepository;

    public void setCaptchaAlgorithm(CaptchaAlgorithm captchaAlgorithm) {
        this.captchaAlgorithm = captchaAlgorithm;
    }

    public void setCaptchaRepository(CaptchaRepository captchaRepository) {
        this.captchaRepository = captchaRepository;
    }

    @Override
    public Captcha generate() {

        // maybe better done less frequently?
        captchaRepository.purgeExpired();

        Captcha captcha = captchaAlgorithm.generate();
        captchaRepository.store(captcha.getToken(), captcha.getResponse());
        return captcha;
    }

    @Override
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

        response = response.trim();

        if(null!=databaseResponse) {
            captchaRepository.delete(token);
            return response.equalsIgnoreCase(databaseResponse);
        }

        return false;
    }

}
