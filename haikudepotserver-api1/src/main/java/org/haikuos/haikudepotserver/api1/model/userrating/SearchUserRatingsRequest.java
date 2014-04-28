/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.userrating;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchRequest;

/**
 * <p>Note that if a value for "pkgVersionMajor" is supplied then it is assumed that the
 * search should incorporate a version.</p>
 */

public class SearchUserRatingsRequest extends AbstractSearchRequest {

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
