/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;
@Deprecated
public class IncrementViewCounterRequest {

    public String architectureCode;

    /**
     * @deprecated use the {@link #repositorySourceCode} instead
     */

    @Deprecated
    public String repositoryCode;

    /**
     * @since 2022-03-23
     */

    public String repositorySourceCode;

    public String name;

    // version coordinates.

    public String major;

    public String minor;

    public String micro;

    public String preRelease;

    public Integer revision;
}
