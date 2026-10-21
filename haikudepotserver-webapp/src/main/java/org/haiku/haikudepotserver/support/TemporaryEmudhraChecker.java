package org.haiku.haikudepotserver.support;

// Temporary test for system state handling for this;
// https://cybersecuritynews.com/emudhra-security-compromise/

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Deprecated // remove in late 2026
@Component
public class TemporaryEmudhraChecker {

    protected static Logger LOGGER = LoggerFactory.getLogger(TemporaryEmudhraChecker.class);

    private final static String ENDPOINT = "https://dv-g3b-emudhra.we-have-a-problem.com/";

    @PostConstruct
    public void startUp() {
        // call out to a site that is using a fake cert from them and see if
        // it errors.

        LOGGER.info("will call [{}]", ENDPOINT);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(new URI(ENDPOINT))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            LOGGER.error("call to [{}] yielded HTTP response [{}]", ENDPOINT, response.statusCode());
        } catch (Exception e) {
            LOGGER.info("unable to call [{}]", ENDPOINT, e);
        }
    }

}
