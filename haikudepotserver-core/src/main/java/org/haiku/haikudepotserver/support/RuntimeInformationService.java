/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Properties;

/**
 * <p>This service is able to provide information about the current runtime.</p>
 */

public class RuntimeInformationService {

    private final long startTimestamp = System.currentTimeMillis();

    private Properties buildProperties = null;

    private Properties getBuildProperties() {
        if (null == buildProperties) {
            buildProperties = new Properties();

            try (InputStream inputStream = RuntimeInformationService.class.getResourceAsStream("/build.properties")) {

                if (null == inputStream) {
                    throw new IllegalStateException("the build properties do not seem to be present.");
                }

                buildProperties.load(inputStream);
            }
            catch(IOException ioe) {
                throw new IllegalStateException("an issue has arisen loading the build properties");
            }
        }

        return buildProperties;
    }

    private String versionOrPlaceholder(String v) {
        return Strings.isNullOrEmpty(v) ? "???" : v;
    }

    public String getProjectVersion() {
        return versionOrPlaceholder(getBuildProperties().getProperty("project.version"));
    }

    public Instant getBuildTimestamp() {
        return Optional.of(getBuildProperties().getProperty("build.timestamp"))
                .filter(StringUtils::isNotBlank)
                .filter(s -> !StringUtils.startsWith(s, "${"))
                // ^^ if the variable is not substituted in the build process
                .map(DateTimeFormatter.ISO_DATE_TIME::parse)
                .map(Instant::from)
                .orElseGet(() -> Instant.ofEpochMilli(0L));
    }

    public String getJavaVersion() {
        return versionOrPlaceholder(System.getProperty("java.version"));
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

}
