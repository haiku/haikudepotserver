/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

public class GetRuntimeInformationResult {

    public String projectVersion;

    public String javaVersion;

    public Long startTimestamp;

    public Integer getBulkPkgRequestLimit;

    public Boolean isProduction;

    /**
     * @since 2018-12-24
     */

    public Defaults defaults;

    /**
     * @since 2019-01-22
     */

    public String dataHandlingInformationUrl;

    public static class Defaults {

        public String architectureCode;

        public String repositoryCode;

    }
}
