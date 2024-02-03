/*
* Copyright 2014-2022, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

/**
 * <p>Note that if a value for "pkgVersionMajor" is supplied then it is assumed that the
 * search should incorporate a version.</p>
 */
@Deprecated
public class SearchUserRatingsRequest extends AbstractSearchRequest {

    /**
     * @since 2015-05-27
     * @deprecated use the {@link #repositorySourceCode} instead
     */

    @Deprecated
    public String repositoryCode;

    /**
     * @since 2022-03-17
     */

    public String repositorySourceCode;

    /**
     * <p>When supplied, will constrain the search to only show user ratings that belong to
     * this nominated user.</p>
     */

    public String userNickname;

    public String pkgName;

    public String pkgVersionArchitectureCode;

    public String pkgVersionMajor;

    public String pkgVersionMinor;

    public String pkgVersionMicro;

    public String pkgVersionPreRelease;

    public Integer pkgVersionRevision;

    /**
     * <p>When supplied, will constrain the search to only show user ratings that have been
     * created since these many days.</p>
     */

    public Number daysSinceCreated;

}
