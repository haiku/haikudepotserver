/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.userrating;

public class GetUserRatingByUserAndPkgVersionRequest {

    public String userNickname;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public String pkgVersionMajor;

    public String pkgVersionMinor;

    public String pkgVersionMicro;

    public String pkgVersionPreRelease;

    public Integer pkgVersionRevision;

}
