/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.userrating;

public abstract class AbstractGetUserRatingResult {

    public String code;

    public String naturalLanguageCode;

    public String userNickname;

    public String userRatingStabilityCode;

    public Boolean active;

    public String comment;

    public Long modifyTimestamp;

    public Long createTimestamp;

    public Short rating;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public String pkgVersionMajor;

    public String pkgVersionMinor;

    public String pkgVersionMicro;

    public String pkgVersionPreRelease;

    public Integer pkgVersionRevision;

}
