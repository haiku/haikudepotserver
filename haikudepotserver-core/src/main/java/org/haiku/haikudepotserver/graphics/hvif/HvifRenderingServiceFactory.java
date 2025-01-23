/*
 * Copyright 2018-2024, Andrew Lindesay
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

    private final String hvif2pngPath;

    private final String graphicsServerBaseUri;

    public HvifRenderingServiceFactory(
            String hvif2pngPath,
            String graphicsServerBaseUri
    ) {
        this.hvif2pngPath = hvif2pngPath;
        this.graphicsServerBaseUri = graphicsServerBaseUri;
    }

    @Override
    public HvifRenderingService getObject() throws Exception {
        if (StringUtils.isNotBlank(graphicsServerBaseUri)) {
            LOGGER.info("will use server hvif rendering [{}]", graphicsServerBaseUri);
            return new ServerHvifRenderingServiceImpl(graphicsServerBaseUri);
        }

        if (StringUtils.isNotBlank(hvif2pngPath)) {
            LOGGER.info("will use hvif2png rendering [{}]", hvif2pngPath);
            return new Hvif2PngHvifRenderingServiceImpl(hvif2pngPath);
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
