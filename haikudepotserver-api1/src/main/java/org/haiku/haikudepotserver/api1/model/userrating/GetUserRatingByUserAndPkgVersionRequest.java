/*
* Copyright 2014-2015, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;

public class GetUserRatingByUserAndPkgVersionRequest {

    /**
     * @since 2015-05-27
     */

    public String repositoryCode;

    public String userNickname;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public String pkgVersionMajor;

    public String pkgVersionMinor;

    public String pkgVersionMicro;

    public String pkgVersionPreRelease;

    public Integer pkgVersionRevision;

}
