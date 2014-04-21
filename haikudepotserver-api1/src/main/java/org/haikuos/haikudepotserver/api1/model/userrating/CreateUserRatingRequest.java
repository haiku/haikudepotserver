/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.userrating;

import org.haikuos.haikudepotserver.api1.model.PkgVersionType;

public class CreateUserRatingRequest {

    public String naturalLanguageCode;

    public String userNickname;

    public String userRatingStabilityCode;

    public String comment;

    public Short rating;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public PkgVersionType pkgVersionType;

}
