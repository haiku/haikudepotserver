/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.hvif;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * <p>Produces an HVIF rendering service depending on the configuration of the application server.</p>
 */

public class HvifRenderingServiceFactory implements FactoryBean<HvifRenderingService> {

    protected final static Logger LOGGER = LoggerFactory.getLogger(HvifRenderingServiceFactory.class);

    private final String graphicsServerBaseUri;

    public HvifRenderingServiceFactory(String graphicsServerBaseUri
    ) {
        this.graphicsServerBaseUri = graphicsServerBaseUri;
    }

    @Override
    public HvifRenderingService getObject() throws Exception {
        if (StringUtils.isNotBlank(graphicsServerBaseUri)) {
            LOGGER.info("will use server hvif rendering [{}]", graphicsServerBaseUri);
            return new ServerHvifRenderingServiceImpl(graphicsServerBaseUri);
        }

        LOGGER.info("will fallback hvif rendering that produces generic images");
        return new FallbackHvifRenderingServiceImpl();
    }

    @Override
    public Class<?> getObjectType() {
        return HvifRenderingService.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
