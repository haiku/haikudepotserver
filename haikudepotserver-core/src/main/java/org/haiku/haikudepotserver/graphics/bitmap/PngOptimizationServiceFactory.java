/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * <p>This will create a PNG optimization service based on the configuration.</p>
 */

public class PngOptimizationServiceFactory implements FactoryBean<PngOptimizationService> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PngOptimizationServiceFactory.class);

    @Value("${optipng.path:}")
    private String optiPngPath;

    @Override
    public PngOptimizationService getObject() throws Exception {
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
