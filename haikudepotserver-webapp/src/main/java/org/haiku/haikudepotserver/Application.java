/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver;

import org.haiku.haikudepotserver.config.ApplicationConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Import;

@SpringBootApplication
public class Application {

    static void main(String[] args) {
        new SpringApplicationBuilder(Application.class)
                .sources(ApplicationConfig.class)
                .profiles("oauth2") // causes the second `application-oauth2.yml` file to be loaded in.
                .run(args);
    }

}
