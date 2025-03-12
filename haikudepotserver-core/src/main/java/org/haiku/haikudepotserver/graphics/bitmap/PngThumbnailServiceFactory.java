/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

public class PngThumbnailServiceFactory implements FactoryBean<PngThumbnailService> {

    protected final static Logger LOGGER = LoggerFactory.getLogger(PngThumbnailServiceFactory.class);

    private final String graphicsServerBaseUri;

    public PngThumbnailServiceFactory(String graphicsServerBaseUri) {
        this.graphicsServerBaseUri = graphicsServerBaseUri;
    }

    @Override
    public PngThumbnailService getObject() {
        if (StringUtils.isNotBlank(graphicsServerBaseUri)) {
            LOGGER.info("will use graphics server [{}]", graphicsServerBaseUri);
            return new ServerPngThumbnailService(graphicsServerBaseUri);
        }

        return new FallbackThumbnailServiceImpl();
    }

    @Override
    public Class<?> getObjectType() {
        return PngThumbnailService.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
