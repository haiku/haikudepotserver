/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import io.avaje.config.Config;
import io.avaje.inject.BeanScope;
import io.avaje.inject.BeanScopeBuilder;
import io.avaje.jex.Jex;
import io.avaje.jex.Routing;

import java.util.Collection;

public class Application {

    public static void main(String[] args) {

        BeanScopeBuilder builder = BeanScope.builder()
                .modules(new GraphicsModule());

        try (BeanScope beanScope = builder.build()) {
            Collection<Routing.HttpService> httpServices = beanScope.list(Routing.HttpService.class);
            Jex.create()
                    .config(jc -> {
                        jc.port(Config.getInt(Constants.KEY_CONFIG_SERVER_PORT, 8080));
                        jc.maxRequestSize(Constants.MAX_REQUEST_SIZE);
                        jc.health(true);
                        jc.compression(cc -> {
                            // There's no point compressing any of the payloads because they are all
                            // pre-compressed graphics data.
                            cc.disableCompression();
                        });
                    })
                    .routing(httpServices)
                    .start();
        }
    }

}
