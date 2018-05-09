/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.userrating;

import org.haiku.haikudepotserver.api1.model.PkgVersionType;

public class CreateUserRatingRequest {

    /**
     * @since 2015-05-27
     */

    public String repositoryCode;

    public String naturalLanguageCode;

    public String userNickname;

    public String userRatingStabilityCode;

    public String comment;

    public Short rating;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    /**
     * @since 2018-02-19
     */

    public String pkgVersionMajor;

    /**
     * @since 2018-02-19
     */

    public String pkgVersionMinor;

    /**
     * @since 2018-02-19
     */

    public String pkgVersionMicro;

    /**
     * @since 2018-02-19
     */

    public String pkgVersionPreRelease;

    /**
     * @since 2018-02-19
     */

    public Integer pkgVersionRevision;

    public PkgVersionType pkgVersionType;

}
