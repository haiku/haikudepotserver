/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Preconditions;
import net.jawr.web.resource.bundle.factory.util.ConfigPropertiesSource;

import java.util.Properties;

/**
 * <p>Subclass of the JAWR properties file loader that can take a static set of
 * properties.</p>
 */

public class StaticConfigPropertiesSource implements ConfigPropertiesSource {

    private static Properties properties;

    @Override
    public Properties getConfigProperties() {
        Preconditions.checkState(null != properties, "the properties must have been set");
        return properties;
    }

    @Override
    public boolean configChanged() {
        return false;
    }

    public static void setProperties(Properties value) {
        Preconditions.checkArgument(null != value, "the properties must be provided");
        properties = value;
    }

}
