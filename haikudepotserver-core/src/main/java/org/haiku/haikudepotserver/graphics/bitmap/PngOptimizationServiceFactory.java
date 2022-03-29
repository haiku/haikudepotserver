/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * <p>This will create a PNG optimization service based on the configuration.</p>
 */

public class PngOptimizationServiceFactory implements FactoryBean<PngOptimizationService> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PngOptimizationServiceFactory.class);

    private final String optiPngPath;

    public PngOptimizationServiceFactory(String optiPngPath) {
        this.optiPngPath = optiPngPath;
    }

    @Override
    public PngOptimizationService getObject() {
        if(!Strings.isNullOrEmpty(optiPngPath)) {
            LOGGER.info("will use optipng; {}", optiPngPath);
            return new OptipngPngOptimizationServiceImpl(optiPngPath);
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
