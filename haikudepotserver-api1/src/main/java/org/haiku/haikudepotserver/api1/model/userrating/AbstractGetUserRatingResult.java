/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;

public abstract class AbstractGetUserRatingResult extends AbstractUserRatingResult {

    public String code;

    public String naturalLanguageCode;

    public User user;

    public String userRatingStabilityCode;

    public Boolean active;

    public String comment;

    public Long modifyTimestamp;

    public Long createTimestamp;

    public Short rating;

    public PkgVersion pkgVersion;

}
