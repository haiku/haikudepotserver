/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * <p>This will create a PNG optimization service based on the configuration.</p>
 */

public class PngOptimizationServiceFactory implements FactoryBean<PngOptimizationService> {

    protected final static Logger LOGGER = LoggerFactory.getLogger(PngOptimizationServiceFactory.class);

    private final String graphicsServerBaseUri;

    public PngOptimizationServiceFactory(
            String graphicsServerBaseUri) {
        this.graphicsServerBaseUri = graphicsServerBaseUri;
    }

    @Override
    public PngOptimizationService getObject() {
        if (StringUtils.isNotBlank(graphicsServerBaseUri)) {
            LOGGER.info("will use graphics server [{}]", graphicsServerBaseUri);
            return new ServerOptimizationServiceImpl(graphicsServerBaseUri);
        }

        LOGGER.info("will no-op png optimization");
        return new NoOpPngOptimizationServiceImpl();
    }

    @Override
    public Class<?> getObjectType() {
        return PngOptimizationService.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
