package org.haiku.haikudepotserver.graphics.support.avaje.config;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.avaje.config.Configuration;
import io.avaje.config.ConfigurationPlugin;

import java.util.Collection;
import java.util.Map;

/**
 * <p>This will take action after the config is loaded and will override any configuration keys
 * with env-vars if there is a suitably named env-var present. This is mirroring the behaviour
 * of SpringBoot.</p>
 */

public class OverridePlugin implements ConfigurationPlugin {

    @Override
    public void apply(Configuration configuration) {
        Map<String, String> overrides = deriveOverrides(configuration.keys());

        if (!overrides.isEmpty()) {
            configuration.eventBuilder(OverridePlugin.class.getSimpleName())
                    .putAll(overrides)
                    .publish();
        }
    }

    private Map<String, String> deriveOverrides(Collection<String> keys) {
        ImmutableMap.Builder<String, String> result = ImmutableMap.builder();
        for (String key : keys) {
            String override = deriveOverride(key);

            if (null != override) {
                result.put(key, override);
            }
        }
        return result.build();
    }

    /**
     * Look to the system to provide an override value; system variables and env-vars.
     */

    private String deriveOverride(String key) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(key));
        String systemPropertiesValue = System.getProperty(key);

        if (null != systemPropertiesValue) {
            return systemPropertiesValue;
        }

        String envValue = System.getenv(mapKeyToEnvVar(key));
        if (null !=  envValue) {
            return envValue;
        }

        return null;
    }

    /**
     * <p>Maps the configuration key to the env-var name that would shadow it. Here the mapping
     * should follow the <a href="https://docs.spring.io/spring-boot/docs/2.6.1/reference/html/features.html#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables">same</a>
     * convention as SpringBoot.</p>
     */

    private static String mapKeyToEnvVar(String key) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(key));
        key = key.replace(".", "_");
        key = key.replace("-", "");
        return key.toUpperCase();
    }

}
