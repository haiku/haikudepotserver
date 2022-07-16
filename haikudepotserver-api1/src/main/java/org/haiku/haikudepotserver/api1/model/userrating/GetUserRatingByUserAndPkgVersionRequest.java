/*
* Copyright 2014-2022, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;
@Deprecated
public class GetUserRatingByUserAndPkgVersionRequest {

    /**
     * @since 2015-05-27
     * @deprecated use the {@link #repositorySourceCode} instead
     */

    public String repositoryCode;

    /**
     * @since 2022-03-17
     */

    public String repositorySourceCode;

    public String userNickname;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public String pkgVersionMajor;

    public String pkgVersionMinor;

    public String pkgVersionMicro;

    public String pkgVersionPreRelease;

    public Integer pkgVersionRevision;

}
