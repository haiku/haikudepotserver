/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web;

import net.jawr.web.resource.bundle.factory.util.ConfigPropertiesSource;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

import java.util.Properties;

/**
 * <p>Subclass of the JAWR properties file loader that can hook in and get a spring-sourced config properties
 * from the spring context.</p>
 */

public class SpringProvidedConfigPropertiesSource implements ConfigPropertiesSource {

    private final static String KEY_PROPERTIESBEAN = "jawrConfigProperties";

    public Properties getConfigProperties() {
        WebApplicationContext ctx = ContextLoader.getCurrentWebApplicationContext();
        Properties properties = (Properties) ctx.getBean(KEY_PROPERTIESBEAN);
        return properties;
    }

    public boolean configChanged() {
        return false;
    }

}
